package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.farm.application.structure.OrchidPlacementPolicy;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.orchid.mutation.CancelOrchidGroupCreationMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.MoveOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationRoutingPolicy;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.application.orchid.mutation.UpdateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupCreateRequest;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupBatchUpdateRequest;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupMoveRequest;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupResponse;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupUpdateRequest;
import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import com.greenhouse.backend.farm.repository.inbound.InboundRecordRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.variety.VarietyRepository;
import com.greenhouse.backend.work.application.target.WorkOrchidGroupUsageInspector;
import java.math.BigDecimal;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * ORCHID-CUTOVER: LEGACY_RETIRE — Engine 경로와 전환 후 제거할 직접 writer를 함께 가진다.
 * Removal gate: 운영 ACTIVE 안정화 및 writer inventory 승인.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class OrchidGroupCommandService {

	private final BedZoneRepository bedZoneRepository;
	private final OrchidGroupRepository orchidGroupRepository;
	private final InboundRecordRepository inboundRecordRepository;
	private final VarietyRepository varietyRepository;
	private final WorkOrchidGroupUsageInspector workUsageInspector;
	private final OrchidPlacementPolicy orchidPlacementPolicy;
	private final OrchidGroupAuditSupport auditSupport;
	private final OrchidGroupMutationEngine mutationEngine;
	private final OrchidGroupMutationRoutingPolicy mutationRoutingPolicy;
	private final Clock clock;

	public OrchidGroupResponse create(OrchidGroupCreateRequest request) {
		var command = mutationRoutingPolicy.routesToEngine()
				? new CreateOrchidGroupMutationCommand(
				OrchidGroupMutationSources.farmRequest(
						"ORCHID_GROUP_COMMAND", "DIRECT", "CREATE"),
				request.bedZoneId(),
				mutationDetails(request),
				TimeConfig.farmToday(clock),
				"난 묶음 등록")
				: null;
		OrchidGroup created = mutationRoutingPolicy.routesToEngine()
				? createWithEngine(command)
				: createEntity(request);
		auditSupport.record(created.getId(), AuditAction.CREATED, AuditSource.ORCHID_GROUP_MANAGEMENT,
				null, auditSupport.snapshot(created), Map.of("creationMode", "SINGLE"));
		return OrchidGroupResponse.from(created);
	}

	public OrchidGroup createEntity(OrchidGroupCreateRequest request) {
		return createEntity(request, Set.of());
	}

	public OrchidGroup createEntity(
			OrchidGroupCreateRequest request,
			Set<Long> placementExclusionOrchidGroupIds) {
		BedZone bedZone = findZone(request.bedZoneId());
		Variety variety = findVariety(request.varietyId());
		if (!variety.isActive()) {
			throw new IllegalArgumentException("비활성 품종으로 난 묶음을 생성할 수 없습니다.");
		}
		BigDecimal startPosition = orchidPlacementPolicy.normalizeNumber(request.startPosition());
		BigDecimal endPosition = orchidPlacementPolicy.normalizeNumber(request.endPosition());
		orchidPlacementPolicy.validatePlacementExcluding(
				bedZone, startPosition, endPosition, placementExclusionOrchidGroupIds);

		int nextSortOrder = orchidGroupRepository.findMaxSortOrderByBedZoneId(bedZone.getId()) + 1;
		OrchidGroup orchidGroup = new OrchidGroup(
				bedZone,
				variety.getGenus(),
				variety.getName(),
				request.quantity(),
				normalize(request.potSize()),
				request.ageYear(),
				normalizeRequired(request.status()),
				nextSortOrder,
				startPosition,
				endPosition);
		orchidGroup.updateDetails(
				variety.getGenus(),
				variety.getName(),
				request.quantity(),
				normalize(request.potSize()),
				request.ageYear(),
				normalizeRequired(request.status()),
				normalize(request.placementType()),
				request.trayCount(),
				request.splitPlacementAllowed(),
				startPosition,
				endPosition,
				normalize(request.memo()));
		orchidGroup.assignVariety(variety);
		return orchidGroupRepository.save(orchidGroup);
	}

	public OrchidGroupResponse update(Long orchidGroupId, OrchidGroupUpdateRequest request) {
		return update(orchidGroupId, request, "SINGLE");
	}

	public List<OrchidGroupResponse> updateBatch(OrchidGroupBatchUpdateRequest request) {
		return request.orchidGroups().stream()
				.map(item -> update(item.orchidGroupId(), item.update(), "BATCH"))
				.toList();
	}

	private OrchidGroupResponse update(Long orchidGroupId, OrchidGroupUpdateRequest request, String correctionMode) {
		OrchidGroup orchidGroup = orchidGroupRepository.findById(orchidGroupId)
				.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
		OrchidGroupAuditSnapshot before = auditSupport.snapshot(orchidGroup);
		OrchidGroupMutationDetails details = mutationRoutingPolicy.routesToEngine()
				? mutationDetails(request)
				: null;
		if (mutationRoutingPolicy.routesToEngine()) {
			if (!hasSameDetails(orchidGroup, details)) {
				mutationEngine.updateDetails(updateCommand(orchidGroupId, details));
			}
			OrchidGroup updated = orchidGroupRepository.findById(orchidGroupId)
					.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
			OrchidGroupAuditSnapshot after = auditSupport.snapshot(updated);
			auditSupport.record(orchidGroupId, auditSupport.actionForCorrection(before, after),
					AuditSource.ORCHID_GROUP_CORRECTION, before, after,
					Map.of("correctionMode", correctionMode));
			return OrchidGroupResponse.from(updated);
		}
		Variety variety = findVariety(request.varietyId());
		BigDecimal startPosition = orchidPlacementPolicy.normalizeNumber(request.startPosition());
		BigDecimal endPosition = orchidPlacementPolicy.normalizeNumber(request.endPosition());
		orchidPlacementPolicy.validatePlacement(orchidGroup.getBedZone(), startPosition, endPosition, orchidGroupId);

		orchidGroup.updateDetails(
				variety.getGenus(),
				variety.getName(),
				request.quantity(),
				normalize(request.potSize()),
				request.ageYear(),
				normalizeRequired(request.status()),
				normalize(request.placementType()),
				request.trayCount(),
				request.splitPlacementAllowed(),
				startPosition,
				endPosition,
				normalize(request.memo()));
		orchidGroup.assignVariety(variety);
		OrchidGroupAuditSnapshot after = auditSupport.snapshot(orchidGroup);
		auditSupport.record(orchidGroupId, auditSupport.actionForCorrection(before, after),
				AuditSource.ORCHID_GROUP_CORRECTION, before, after, Map.of("correctionMode", correctionMode));
		return OrchidGroupResponse.from(orchidGroup);
	}

	public void delete(Long orchidGroupId) {
		OrchidGroup orchidGroup = orchidGroupRepository.findById(orchidGroupId)
				.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
		OrchidGroupAuditSnapshot before = auditSupport.snapshot(orchidGroup);
		if (workUsageInspector.hasEffectReference(orchidGroupId)) {
			throw new ConflictException("작업 이력과 연결된 난 묶음은 삭제할 수 없습니다. 작업 취소, 보정 또는 폐기 작업으로 처리해주세요.");
		}
		var command = mutationRoutingPolicy.routesToEngine()
				? new CancelOrchidGroupCreationMutationCommand(
					OrchidGroupMutationSources.farmRequest(
							"ORCHID_GROUP_COMMAND", orchidGroupId.toString(), "CANCEL_CREATION"),
					orchidGroupId,
					TimeConfig.farmToday(clock),
					"난 묶음 삭제 요청에 따른 생성 취소")
				: null;
		if (mutationRoutingPolicy.routesToEngine()) {
			mutationEngine.cancelCreation(command);
			auditSupport.record(orchidGroupId, AuditAction.DEACTIVATED,
					AuditSource.ORCHID_GROUP_MANAGEMENT,
					before,
					auditSupport.snapshot(orchidGroup),
					Map.of("deleteMode", "CANCEL_CREATION"));
			return;
		}
		inboundRecordRepository.clearCreatedOrchidGroup(orchidGroupId);
		orchidGroupRepository.delete(orchidGroup);
		auditSupport.record(orchidGroupId, AuditAction.DELETED, AuditSource.ORCHID_GROUP_MANAGEMENT,
				before, null, Map.of());
	}

	public OrchidGroupResponse moveForOperation(Long orchidGroupId, OrchidGroupMoveRequest request) {
		return moveForOperation(orchidGroupId, request, true);
	}

	OrchidGroupResponse moveLegacyForOperation(Long orchidGroupId, OrchidGroupMoveRequest request) {
		return moveForOperation(orchidGroupId, request, false);
	}

	private OrchidGroupResponse moveForOperation(
			Long orchidGroupId,
			OrchidGroupMoveRequest request,
			boolean applyRouting) {
		OrchidGroup orchidGroup = orchidGroupRepository.findById(orchidGroupId)
				.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
		BedZone toBedZone = findZone(request.toBedZoneId());
		BigDecimal startPosition = orchidPlacementPolicy.normalizeNumber(request.startPosition());
		BigDecimal endPosition = orchidPlacementPolicy.normalizeNumber(request.endPosition());
		orchidPlacementPolicy.validatePlacement(toBedZone, startPosition, endPosition, orchidGroupId);

		Long fromBedZoneId = orchidGroup.getBedZone().getId();
		if (fromBedZoneId.equals(toBedZone.getId())
				&& equalPosition(orchidGroup.getStartPosition(), startPosition)
				&& equalPosition(orchidGroup.getEndPosition(), endPosition)) {
			return OrchidGroupResponse.from(orchidGroup);
		}
		var command = applyRouting && mutationRoutingPolicy.routesToEngine()
				? new MoveOrchidGroupMutationCommand(
					OrchidGroupMutationSources.farmRequest(
							"ORCHID_GROUP_COMMAND", orchidGroupId.toString(), "MOVE"),
					orchidGroupId,
					request.toBedZoneId(),
					startPosition,
					endPosition,
					TimeConfig.farmToday(clock),
					request.memo())
				: null;
		if (applyRouting && mutationRoutingPolicy.routesToEngine()) {
			mutationEngine.move(command);
			return OrchidGroupResponse.from(orchidGroup);
		}
		if (!fromBedZoneId.equals(toBedZone.getId())) {
			int nextSortOrder = orchidGroupRepository.findMaxSortOrderByBedZoneId(toBedZone.getId()) + 1;
			orchidGroup.moveTo(toBedZone, nextSortOrder, startPosition, endPosition);
		} else {
			orchidGroup.moveTo(toBedZone, orchidGroup.getSortOrder(), startPosition, endPosition);
		}
		return OrchidGroupResponse.from(orchidGroup);
	}

	private OrchidGroup createWithEngine(CreateOrchidGroupMutationCommand command) {
		var result = mutationEngine.create(command);
		Long orchidGroupId = result.entries().getFirst().orchidGroupId();
		return orchidGroupRepository.findById(orchidGroupId)
				.orElseThrow(() -> new NotFoundException("생성된 난 묶음을 찾을 수 없습니다."));
	}

	private UpdateOrchidGroupMutationCommand updateCommand(
			Long orchidGroupId,
			OrchidGroupMutationDetails details) {
		return new UpdateOrchidGroupMutationCommand(
				OrchidGroupMutationSources.farmRequest(
						"ORCHID_GROUP_COMMAND", orchidGroupId.toString(), "UPDATE"),
				orchidGroupId,
				details,
				TimeConfig.farmToday(clock),
				"난 묶음 상세 수정");
	}

	private OrchidGroupMutationDetails mutationDetails(OrchidGroupCreateRequest request) {
		return new OrchidGroupMutationDetails(
				request.varietyId(), request.quantity(), request.potSize(), request.ageYear(),
				request.status(), request.placementType(), request.trayCount(),
				request.splitPlacementAllowed(), request.startPosition(), request.endPosition(), request.memo());
	}

	private OrchidGroupMutationDetails mutationDetails(OrchidGroupUpdateRequest request) {
		return new OrchidGroupMutationDetails(
				request.varietyId(), request.quantity(), request.potSize(), request.ageYear(),
				request.status(), request.placementType(), request.trayCount(),
				request.splitPlacementAllowed(), request.startPosition(), request.endPosition(), request.memo());
	}

	private boolean hasSameDetails(OrchidGroup group, OrchidGroupMutationDetails details) {
		return group.getVariety() != null && group.getVariety().getId().equals(details.varietyId())
				&& java.util.Objects.equals(group.getQuantity(), details.quantity())
				&& java.util.Objects.equals(group.getPotSize(), details.potSize())
				&& java.util.Objects.equals(group.getAgeYear(), details.ageYear())
				&& java.util.Objects.equals(group.getStatus(), details.status())
				&& java.util.Objects.equals(group.getPlacementType(), details.placementType())
				&& java.util.Objects.equals(group.getTrayCount(), details.trayCount())
				&& java.util.Objects.equals(group.getSplitPlacementAllowed(), details.splitPlacementAllowed())
				&& equalPosition(group.getStartPosition(), details.startPosition())
				&& equalPosition(group.getEndPosition(), details.endPosition())
				&& java.util.Objects.equals(group.getMemo(), details.memo());
	}

	private boolean equalPosition(BigDecimal currentValue, BigDecimal requestValue) {
		if (currentValue == null && requestValue == null) {
			return true;
		}
		if (currentValue == null || requestValue == null) {
			return false;
		}
		return currentValue.compareTo(requestValue) == 0;
	}

	private BedZone findZone(Long bedZoneId) {
		return bedZoneRepository.findWithDetailsById(bedZoneId)
				.orElseThrow(() -> new NotFoundException("논리 구역을 찾을 수 없습니다."));
	}

	private Variety findVariety(Long varietyId) {
		return varietyRepository.findById(varietyId)
				.orElseThrow(() -> new NotFoundException("품종을 찾을 수 없습니다."));
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String normalizeRequired(String value) {
		String normalized = normalize(value);
		if (normalized == null) {
			throw new IllegalArgumentException("필수 문자열 값은 비워둘 수 없습니다.");
		}
		return normalized;
	}
}
