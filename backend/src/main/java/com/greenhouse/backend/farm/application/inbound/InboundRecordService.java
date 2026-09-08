package com.greenhouse.backend.farm.application.inbound;

import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateInboundOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupMutationItem;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationRoutingPolicy;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ORCHID-CUTOVER: LEGACY_RETIRE — Engine 경로와 전환 후 제거할 직접 생성 분기를 함께 가진다. Removal gate: 운영
 * ACTIVE 안정화 및 writer inventory 승인.
 */
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

	private final OrchidGroupMutationRoutingPolicy mutationRoutingPolicy;

	public InboundRecordResponse create(InboundRecordCreateCommand request) {
		InboundStatus status = resolveCreateStatus(request);
		validateCreate(request, status);
		Variety variety = varietyService.resolveInboundVariety(request.varietyId(), request.newVariety());
		BedZone bedZone = requiresPlacement(request.inboundType()) ? findBedZone(request.bedZoneId()) : null;
		InboundRecord inboundRecord = new InboundRecord(request.inboundDate(), request.inboundType(), variety, status,
				request.bottleCount(), request.estimatedQuantity(), request.actualQuantity(),
				normalize(request.tempLocation()), request.pottingDueDate(), normalize(request.potSize()),
				request.ageYear(), normalize(request.growthStage()), normalize(request.placementType()),
				request.trayCount(), bedZone, requestActorProvider.resolve(request.worker()),
				normalize(request.memo()));
		InboundRecord saved = inboundRecordRepository.save(inboundRecord);
		WorkMutationLink mutationLink = null;

		if (requiresPlacement(request.inboundType())) {
			OrchidGroup orchidGroup;
			OrchidPlacementPolicy.PlacementRange placementRange = orchidPlacementPolicy.resolveRange(bedZone,
					request.startPosition(), request.endPosition());
			if (mutationRoutingPolicy.routesToEngine()) {
				var mutationCommand = new CreateInboundOrchidGroupsMutationCommand(
						OrchidGroupMutationSources.inbound(saved.getId(), "CREATE_PLACED_GROUP"), saved.getId(),
						List.of(new CreateOrchidGroupMutationItem(bedZone.getId(),
								new OrchidGroupMutationDetails(variety.getId(),
										InboundRecord.resolveQuantity(request.actualQuantity(),
												request.estimatedQuantity()),
										request.potSize(), request.ageYear(), DEFAULT_ORCHID_STATUS,
										request.placementType(), request.trayCount(), false,
										placementRange.startPosition(), placementRange.endPosition(), request.memo()))),
						request.inboundDate(), "즉시 배치 입고");
				var mutation = mutationEngine.createFromInbound(mutationCommand);
				Long orchidGroupId = mutation.entries().getFirst().orchidGroupId();
				orchidGroup = orchidGroupRepository.findById(orchidGroupId)
					.orElseThrow(() -> new NotFoundException("생성된 난 묶음을 찾을 수 없습니다."));
				mutationLink = new WorkMutationLink(mutation.mutationId(), mutation.correlationId());
			}
			else {
				orchidGroup = createPlacedOrchidGroup(variety, request, bedZone, placementRange);
				orchidGroup.assignVariety(variety);
				orchidGroup.assignInboundRecord(saved);
				orchidGroupRepository.save(orchidGroup);
			}
			saved.place(bedZone, orchidGroup, request.inboundDate(),
					InboundRecord.resolveQuantity(request.actualQuantity(), request.estimatedQuantity()));
		}
		else {
			saved.markPottingPending(status);
		}
		inboundWorkOperationRecorder.record(workOperationRequestFactory.create(saved), mutationLink);
		return InboundRecordResponse.from(inboundRecordFinder.find(saved.getId()));
	}

	public InboundRecordResponse update(Long inboundRecordId, InboundRecordUpdateRequest request) {
		InboundRecord inboundRecord = inboundRecordFinder.find(inboundRecordId);
		Map<String, Object> before = auditSupport.snapshot(inboundRecord);
		inboundRecord.updateMetadata(request.inboundDate(), request.bottleCount(), request.estimatedQuantity(),
				request.actualQuantity(), normalize(request.tempLocation()), request.pottingDueDate(),
				normalize(request.potSize()), request.ageYear(), normalize(request.growthStage()),
				normalize(request.placementType()), request.trayCount(), requestActorProvider.resolve(request.worker()),
				normalize(request.memo()));
		auditSupport.record(AuditAction.UPDATED, inboundRecord, before, auditSupport.snapshot(inboundRecord));
		return InboundRecordResponse.from(inboundRecord);
	}

	public InboundRecordResponse cancel(Long inboundRecordId, InboundRecordCancelRequest request) {
		InboundRecord inboundRecord = inboundRecordFinder.find(inboundRecordId);
		inboundRecord.requireCancellable();
		Map<String, Object> before = auditSupport.snapshot(inboundRecord);
		inboundWorkOperationLifecycleService.cancelForInboundRecord(inboundRecordId);
		inboundRecord.cancel(normalize(request.memo()));
		auditSupport.record(AuditAction.DEACTIVATED, inboundRecord, before, auditSupport.snapshot(inboundRecord));
		return InboundRecordResponse.from(inboundRecord);
	}

	public void delete(Long inboundRecordId) {
		InboundRecord inboundRecord = inboundRecordFinder.find(inboundRecordId);
		Map<String, Object> before = auditSupport.snapshot(inboundRecord);
		inboundRecord.requireDeletable();
		inboundRecordRepository.delete(inboundRecord);
		auditSupport.record(AuditAction.DELETED, inboundRecord, before, null);
	}

	private void validateCreate(InboundRecordCreateCommand request, InboundStatus status) {
		if (request.varietyId() == null && request.newVariety() == null) {
			throw new IllegalArgumentException("품종을 선택하거나 새 품종을 입력해야 합니다.");
		}
		if (status == InboundStatus.POTTING_IN_PROGRESS) {
			throw new IllegalArgumentException("작업중 상태는 포트 작업 계획 생성 시 자동으로 설정됩니다.");
		}
		if (request.inboundType() == InboundType.FLASK_SEEDLING) {
			if (request.estimatedQuantity() == null) {
				throw new IllegalArgumentException("유리병 모종은 예상 수량이 필요합니다.");
			}
			if (status == InboundStatus.POTTING_PENDING && request.pottingDueDate() == null) {
				throw new IllegalArgumentException("포트 작업 대기 상태는 예정일이 필요합니다.");
			}
			return;
		}
		if (request.actualQuantity() == null || request.bedZoneId() == null) {
			throw new IllegalArgumentException("즉시 배치 입고는 실제 수량과 배치 구역이 필요합니다.");
		}
	}

	private InboundStatus resolveCreateStatus(InboundRecordCreateCommand request) {
		if (request.status() != null) {
			return request.status();
		}
		if (request.inboundType() == InboundType.FLASK_SEEDLING) {
			return request.pottingDueDate() == null ? InboundStatus.TEMP_STORED : InboundStatus.POTTING_PENDING;
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

	private OrchidGroup createPlacedOrchidGroup(Variety variety, InboundRecordCreateCommand request, BedZone bedZone,
			OrchidPlacementPolicy.PlacementRange placementRange) {
		OrchidGroup orchidGroup = new OrchidGroup(bedZone, variety.getGenus(), variety.getName(),
				InboundRecord.resolveQuantity(request.actualQuantity(), request.estimatedQuantity()),
				normalize(request.potSize()), request.ageYear(), DEFAULT_ORCHID_STATUS,
				orchidGroupRepository.findMaxSortOrderByBedZoneId(bedZone.getId()) + 1, placementRange.startPosition(),
				placementRange.endPosition());
		orchidGroup.updateDetails(variety.getGenus(), variety.getName(),
				InboundRecord.resolveQuantity(request.actualQuantity(), request.estimatedQuantity()),
				normalize(request.potSize()), request.ageYear(), DEFAULT_ORCHID_STATUS,
				normalize(request.placementType()), request.trayCount(), false, placementRange.startPosition(),
				placementRange.endPosition(), normalize(request.memo()));
		return orchidGroup;
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

}
