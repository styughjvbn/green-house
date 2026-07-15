package com.greenhouse.backend.work.dto;

import com.greenhouse.backend.work.domain.WorkOperationTarget;
import com.greenhouse.backend.work.domain.WorkEffectOrchidGroup;
import com.greenhouse.backend.work.domain.WorkRecord;
import com.greenhouse.backend.work.domain.WorkSourceScopeType;
import java.time.LocalDate;
import java.util.Map;

public record OrchidGroupWorkHistoryResponse(
		String sourceKind,
		Long workOperationId,
		Long legacyWorkRecordId,
		Long workTypeId,
		String workType,
		String title,
		LocalDate workDate,
		String status,
		boolean propagated,
		WorkSourceScopeType sourceScopeType,
		Long sourceScopeId,
		Map<String, Object> locationSnapshot,
		Map<String, Object> currentLocation,
		String worker,
		String memo) {

	public static OrchidGroupWorkHistoryResponse from(
			WorkOperationTarget target,
			Map<String, Object> currentLocation) {
		var operation = target.getWorkOperation();
		return new OrchidGroupWorkHistoryResponse(
				"WORK_OPERATION",
				operation.getId(),
				null,
				operation.getWorkType().getId(),
				operation.getWorkType().getName(),
				operation.getTitle(),
				operation.getPlannedStartDate(),
				operation.getStatus().name(),
				operation.getSourceScopeType() != WorkSourceScopeType.ORCHID_GROUP,
				operation.getSourceScopeType(),
				operation.getSourceScopeId(),
				target.getLocationSnapshot(),
				currentLocation,
				operation.getWorker(),
				operation.getMemo());
	}

	public static OrchidGroupWorkHistoryResponse fromLegacy(
			WorkRecord record,
			Map<String, Object> currentLocation) {
		return new OrchidGroupWorkHistoryResponse(
				"LEGACY_WORK_RECORD",
				null,
				record.getId(),
				record.getWorkTypeRef() == null ? null : record.getWorkTypeRef().getId(),
				record.getWorkType(),
				record.getWorkType(),
				record.getWorkDate(),
				record.getStatus().name(),
				false,
				WorkSourceScopeType.ORCHID_GROUP,
				record.getTargetId(),
				null,
				currentLocation,
				record.getWorker(),
				record.getMemo());
	}

	public static OrchidGroupWorkHistoryResponse fromEffect(
			WorkEffectOrchidGroup effectOrchidGroup,
			Map<String, Object> currentLocation) {
		var operation = effectOrchidGroup.getWorkAppliedEffect().getWorkOperation();
		return new OrchidGroupWorkHistoryResponse(
				"WORK_OPERATION_EFFECT",
				operation.getId(),
				null,
				operation.getWorkType().getId(),
				operation.getWorkType().getName(),
				operation.getTitle(),
				operation.getPlannedStartDate(),
				operation.getStatus().name(),
				true,
				operation.getSourceScopeType(),
				operation.getSourceScopeId(),
				null,
				currentLocation,
				operation.getWorker(),
				operation.getMemo());
	}
}
