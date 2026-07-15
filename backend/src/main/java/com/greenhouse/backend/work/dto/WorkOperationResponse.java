package com.greenhouse.backend.work.dto;

import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationStatus;
import com.greenhouse.backend.work.domain.WorkSourceScopeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record WorkOperationResponse(
		Long id,
		Long workTypeId,
		String workType,
		String title,
		WorkOperationStatus status,
		LocalDate plannedStartDate,
		LocalDate plannedEndDate,
		LocalDateTime actualStartAt,
		LocalDateTime actualEndAt,
		WorkSourceScopeType sourceScopeType,
		Long sourceScopeId,
		Map<String, Object> sourceConditionSnapshot,
		LocalDateTime targetSnapshotAt,
		Map<String, Object> details,
		String worker,
		String memo,
		List<WorkOperationTargetResponse> targets) {

	public static WorkOperationResponse from(WorkOperation operation, List<WorkOperationTargetResponse> targets) {
		return new WorkOperationResponse(
				operation.getId(),
				operation.getWorkType().getId(),
				operation.getWorkType().getName(),
				operation.getTitle(),
				operation.getStatus(),
				operation.getPlannedStartDate(),
				operation.getPlannedEndDate(),
				operation.getActualStartAt(),
				operation.getActualEndAt(),
				operation.getSourceScopeType(),
				operation.getSourceScopeId(),
				operation.getSourceConditionSnapshot(),
				operation.getTargetSnapshotAt(),
				operation.getDetails(),
				operation.getWorker(),
				operation.getMemo(),
				targets);
	}
}
