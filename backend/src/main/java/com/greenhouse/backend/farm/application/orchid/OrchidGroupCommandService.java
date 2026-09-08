package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.orchid.mutation.CancelOrchidGroupCreationMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.application.orchid.mutation.UpdateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupBatchUpdateRequest;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupCreateRequest;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupResponse;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupUpdateRequest;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.application.target.WorkOrchidGroupUsageInspector;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class OrchidGroupCommandService {

	private final OrchidGroupRepository orchidGroupRepository;

	private final WorkOrchidGroupUsageInspector workUsageInspector;

	private final OrchidGroupAuditSupport auditSupport;

	private final OrchidGroupMutationEngine mutationEngine;

	private final Clock clock;

	public OrchidGroupResponse create(OrchidGroupCreateRequest request) {
		var businessDate = TimeConfig.farmToday(clock);
		var command = new CreateOrchidGroupMutationCommand(
				OrchidGroupMutationSources.farmRequest("ORCHID_GROUP_COMMAND", "DIRECT", "CREATE"), request.bedZoneId(),
				mutationDetails(request), businessDate, "난 묶음 등록");
		OrchidGroup created = createWithEngine(command);
		auditSupport.record(created.getId(), AuditAction.CREATED, AuditSource.ORCHID_GROUP_MANAGEMENT, null,
				auditSupport.snapshot(created), Map.of("creationMode", "SINGLE"));
		return OrchidGroupResponse.from(created, businessDate);
	}

	public OrchidGroupResponse update(Long orchidGroupId, OrchidGroupUpdateRequest request) {
		return update(orchidGroupId, request, "SINGLE");
	}

	public List<OrchidGroupResponse> updateBatch(OrchidGroupBatchUpdateRequest request) {
		return request.orchidGroups()
			.stream()
			.map(item -> update(item.orchidGroupId(), item.update(), "BATCH"))
			.toList();
	}

	private OrchidGroupResponse update(Long orchidGroupId, OrchidGroupUpdateRequest request, String correctionMode) {
		var businessDate = TimeConfig.farmToday(clock);
		OrchidGroup orchidGroup = orchidGroupRepository.findById(orchidGroupId)
			.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
		OrchidGroupAuditSnapshot before = auditSupport.snapshot(orchidGroup);
		OrchidGroupMutationDetails details = mutationDetails(request);
		if (!hasSameDetails(orchidGroup, details)) {
			mutationEngine.updateDetails(updateCommand(orchidGroupId, details));
		}
		OrchidGroup updated = orchidGroupRepository.findById(orchidGroupId)
			.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
		OrchidGroupAuditSnapshot after = auditSupport.snapshot(updated);
		auditSupport.record(orchidGroupId, auditSupport.actionForCorrection(before, after),
				AuditSource.ORCHID_GROUP_CORRECTION, before, after, Map.of("correctionMode", correctionMode));
		return OrchidGroupResponse.from(updated, businessDate);
	}

	public void delete(Long orchidGroupId) {
		OrchidGroup orchidGroup = orchidGroupRepository.findById(orchidGroupId)
			.orElseThrow(() -> new NotFoundException("난 묶음을 찾을 수 없습니다."));
		OrchidGroupAuditSnapshot before = auditSupport.snapshot(orchidGroup);
		if (workUsageInspector.hasEffectReference(orchidGroupId)) {
			throw new ConflictException("작업 이력과 연결된 난 묶음은 삭제할 수 없습니다. 작업 취소, 보정 또는 폐기 작업으로 처리해주세요.");
		}
		var command = new CancelOrchidGroupCreationMutationCommand(OrchidGroupMutationSources
			.farmRequest("ORCHID_GROUP_COMMAND", orchidGroupId.toString(), "CANCEL_CREATION"), orchidGroupId,
				TimeConfig.farmToday(clock), "난 묶음 삭제 요청에 따른 생성 취소");
		mutationEngine.cancelCreation(command);
		auditSupport.record(orchidGroupId, AuditAction.DEACTIVATED, AuditSource.ORCHID_GROUP_MANAGEMENT, before,
				auditSupport.snapshot(orchidGroup), Map.of("deleteMode", "CANCEL_CREATION"));
	}

	private OrchidGroup createWithEngine(CreateOrchidGroupMutationCommand command) {
		var result = mutationEngine.create(command);
		Long orchidGroupId = result.entries().getFirst().orchidGroupId();
		return orchidGroupRepository.findById(orchidGroupId)
			.orElseThrow(() -> new NotFoundException("생성된 난 묶음을 찾을 수 없습니다."));
	}

	private UpdateOrchidGroupMutationCommand updateCommand(Long orchidGroupId, OrchidGroupMutationDetails details) {
		return new UpdateOrchidGroupMutationCommand(
				OrchidGroupMutationSources.farmRequest("ORCHID_GROUP_COMMAND", orchidGroupId.toString(), "UPDATE"),
				orchidGroupId, details, TimeConfig.farmToday(clock), "난 묶음 상세 수정");
	}

	private OrchidGroupMutationDetails mutationDetails(OrchidGroupCreateRequest request) {
		return new OrchidGroupMutationDetails(request.varietyId(), request.quantity(), request.potSize(),
				request.ageYear(), request.status(), request.placementType(), request.trayCount(),
				request.splitPlacementAllowed(), request.startPosition(), request.endPosition(), request.memo());
	}

	private OrchidGroupMutationDetails mutationDetails(OrchidGroupUpdateRequest request) {
		return new OrchidGroupMutationDetails(request.varietyId(), request.quantity(), request.potSize(),
				request.ageYear(), request.status(), request.placementType(), request.trayCount(),
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

}
