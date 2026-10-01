package com.greenhouse.backend.farm.application.inbound;

import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateInboundOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupMutationItem;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.application.structure.OrchidPlacementPolicy;
import com.greenhouse.backend.farm.application.variety.VarietyService;
import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import com.greenhouse.backend.farm.domain.inbound.InboundStatus;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordCancelRequest;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordPottingVoidRequest;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordResponse;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordUpdateRequest;
import com.greenhouse.backend.farm.repository.inbound.InboundRecordRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import com.greenhouse.backend.work.application.effect.WorkMutationLink;
import com.greenhouse.backend.work.application.operation.InboundWorkOperationLifecycleService;
import com.greenhouse.backend.work.application.operation.InboundWorkOperationRecorder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class InboundRecordService {

	private static final String DEFAULT_ORCHID_STATUS = "정상";

	private final InboundRecordRepository inboundRecordRepository;

	private final VarietyService varietyService;

	private final BedZoneRepository bedZoneRepository;

	private final OrchidGroupRepository orchidGroupRepository;

	private final InboundWorkOperationRecorder inboundWorkOperationRecorder;

	private final InboundWorkOperationLifecycleService inboundWorkOperationLifecycleService;

	private final OrchidPlacementPolicy orchidPlacementPolicy;

	private final InboundRecordFinder inboundRecordFinder;

	private final InboundWorkOperationRequestFactory workOperationRequestFactory;

	private final RequestActorProvider requestActorProvider;

	private final InboundRecordAuditSupport auditSupport;

	private final OrchidGroupMutationEngine mutationEngine;

	private final InboundRecordResponseAssembler responseAssembler;

	public InboundRecordResponse create(InboundRecordCreateCommand request) {
		InboundStatus status = resolveCreateStatus(request);
		validateCreate(request);
		Variety variety = varietyService.resolveInboundVariety(request.varietyId(), request.newVariety());
		InboundPlacementInput placement = request.placement();
		BedZone bedZone = placement == null ? null : findBedZone(placement.bedZoneId());
		InboundRecord inboundRecord = new InboundRecord(request.inboundDate(), request.inboundType(), variety, status,
				request.estimatedQuantity(), normalize(request.tempLocation()), request.pottingDueDate(),
				requestActorProvider.resolve(request.worker()), normalize(request.memo()));
		InboundRecord saved = inboundRecordRepository.save(inboundRecord);
		WorkMutationLink mutationLink = null;
		List<OrchidGroup> createdGroups = List.of();

		if (requiresPlacement(request.inboundType())) {
			OrchidPlacementPolicy.PlacementRange placementRange = orchidPlacementPolicy.resolveRange(bedZone,
					placement.startPosition(), placement.endPosition());
			var mutationCommand = new CreateInboundOrchidGroupsMutationCommand(
					OrchidGroupMutationSources.inbound(saved.getId(), "CREATE_PLACED_GROUP"), saved.getId(),
					List.of(new CreateOrchidGroupMutationItem(bedZone.getId(),
							new OrchidGroupMutationDetails(variety.getId(), placement.quantity(), placement.potSize(),
									placement.ageYear(), DEFAULT_ORCHID_STATUS, placement.placementType(),
									placement.trayCount(), false, placementRange.startPosition(),
									placementRange.endPosition(), request.memo()))),
					request.inboundDate(), "즉시 배치 입고");
			var mutation = mutationEngine.createFromInbound(mutationCommand);
			List<Long> orchidGroupIds = mutation.entries().stream().map(entry -> entry.orchidGroupId()).toList();
			createdGroups = orchidGroupRepository.findAllById(orchidGroupIds);
			if (createdGroups.size() != orchidGroupIds.size()) {
				throw new NotFoundException("생성된 난 묶음을 찾을 수 없습니다.");
			}
			mutationLink = new WorkMutationLink(mutation.mutationId(), mutation.correlationId());
			saved.markPlaced();
		}
		else {
			saved.markPottingPending(status);
		}
		inboundWorkOperationRecorder.record(workOperationRequestFactory.create(saved, createdGroups), mutationLink);
		return responseAssembler.assemble(inboundRecordFinder.find(saved.getId()));
	}

	public InboundRecordResponse update(Long inboundRecordId, InboundRecordUpdateRequest request) {
		InboundRecord inboundRecord = inboundRecordFinder.find(inboundRecordId);
		Map<String, Object> before = auditSupport.snapshot(inboundRecord);
		inboundRecord.updateMetadata(request.inboundDate(), request.estimatedQuantity(),
				normalize(request.tempLocation()), request.pottingDueDate(),
				requestActorProvider.resolve(request.worker()), normalize(request.memo()));
		auditSupport.record(AuditAction.UPDATED, inboundRecord, before, auditSupport.snapshot(inboundRecord));
		return responseAssembler.assemble(inboundRecord);
	}

	public InboundRecordResponse cancel(Long inboundRecordId, InboundRecordCancelRequest request) {
		InboundRecord inboundRecord = inboundRecordFinder.find(inboundRecordId);
		if (inboundRecord.getStatus() == InboundStatus.CANCELED) {
			return responseAssembler.assemble(inboundRecord);
		}
		Map<String, Object> before = auditSupport.snapshot(inboundRecord);
		String reason = normalize(request.memo()) == null ? "입고 취소" : normalize(request.memo());
		String requestKey = resolveRequestKey(request.idempotencyKey(), "inbound-cancel", inboundRecordId);
		if (inboundRecord.getStatus() == InboundStatus.PLACED) {
			if (inboundRecord.getInboundType() == InboundType.FLASK_SEEDLING) {
				inboundWorkOperationLifecycleService.voidPottingForInboundRecord(inboundRecordId,
						childRequestKey(requestKey, "potting"), reason);
			}
			else {
				inboundWorkOperationLifecycleService.voidInboundRegistrationForCancellation(inboundRecordId,
						childRequestKey(requestKey, "registration"), reason);
			}
		}
		inboundRecord.requireCancellable();
		inboundWorkOperationLifecycleService.cancelForInboundRecord(inboundRecordId);
		inboundRecord.cancel(normalize(request.memo()));
		auditSupport.record(AuditAction.DEACTIVATED, inboundRecord, before, auditSupport.snapshot(inboundRecord));
		return responseAssembler.assemble(inboundRecord);
	}

	public InboundRecordResponse voidPotting(Long inboundRecordId, InboundRecordPottingVoidRequest request) {
		InboundRecord inboundRecord = inboundRecordFinder.find(inboundRecordId);
		inboundRecord.requirePottingVoidAllowed();
		Map<String, Object> before = auditSupport.snapshot(inboundRecord);
		inboundWorkOperationLifecycleService.voidPottingForInboundRecord(inboundRecordId,
				resolveRequestKey(request.idempotencyKey(), "inbound-potting-void", inboundRecordId), request.reason());
		auditSupport.record(AuditAction.UPDATED, inboundRecord, before, auditSupport.snapshot(inboundRecord));
		return responseAssembler.assemble(inboundRecord);
	}

	private void validateCreate(InboundRecordCreateCommand request) {
		if (request.varietyId() == null && request.newVariety() == null) {
			throw new IllegalArgumentException("품종을 선택하거나 새 품종을 입력해야 합니다.");
		}
		if (request.inboundType() == InboundType.FLASK_SEEDLING) {
			if (request.estimatedQuantity() == null) {
				throw new IllegalArgumentException("유리병 모종은 예상 수량이 필요합니다.");
			}
			if (request.placement() != null) {
				throw new IllegalArgumentException("유리병 모종 입고에는 즉시 배치 결과를 입력할 수 없습니다.");
			}
			return;
		}
		if (request.placement() == null) {
			throw new IllegalArgumentException("즉시 배치 입고는 난 묶음 배치 결과가 필요합니다.");
		}
	}

	private InboundStatus resolveCreateStatus(InboundRecordCreateCommand request) {
		if (request.inboundType() == InboundType.FLASK_SEEDLING) {
			return InboundStatus.POTTING_PENDING;
		}
		return InboundStatus.PLACED;
	}

	private boolean requiresPlacement(InboundType inboundType) {
		return inboundType != InboundType.FLASK_SEEDLING;
	}

	private BedZone findBedZone(Long bedZoneId) {
		if (bedZoneId == null) {
			throw new IllegalArgumentException("배치 구역이 필요합니다.");
		}
		return bedZoneRepository.findWithDetailsById(bedZoneId)
			.orElseThrow(() -> new NotFoundException("논리 구역을 찾을 수 없습니다."));
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String resolveRequestKey(String provided, String prefix, Long inboundRecordId) {
		String normalized = normalize(provided);
		String generated = prefix + "-" + inboundRecordId + "-" + UUID.randomUUID();
		return normalized == null ? generated.substring(0, Math.min(100, generated.length())) : normalized;
	}

	private String childRequestKey(String requestKey, String suffix) {
		String childSuffix = "-" + suffix;
		return requestKey.substring(0, Math.min(requestKey.length(), 100 - childSuffix.length())) + childSuffix;
	}

}
