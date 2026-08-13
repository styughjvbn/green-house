package com.greenhouse.backend.work.dto.operation;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.domain.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.domain.operation.WorkTypeWorkflow;
import com.greenhouse.backend.work.dto.target.WorkOperationTargetResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record WorkOperationResponse(
		Long id,
		Long workTypeId,
		String workTypeCode,
		String workType,
		WorkTypeTemplate workTypeTemplate,
		WorkTypeWorkflow workTypeWorkflow,
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
		WorkOperationProgressResponse progress,
		List<WorkOperationTargetResponse> targets,
		List<WorkOperationAction> availableActions) {

	public static WorkOperationResponse from(
			WorkOperation operation,
			List<WorkOperationTargetResponse> targets,
			List<WorkOperationAction> availableActions) {
		return new WorkOperationResponse(
				operation.getId(),
				operation.getWorkType().getId(),
				operation.getWorkType().getCode(),
				operation.getWorkType().getName(),
				operation.getWorkType().getTemplate(),
				operation.getWorkType().workflow(),
				operation.getTitle(),
				operation.getStatus(),
				operation.getPlannedStartDate(),
				operation.getPlannedEndDate(),
				TimeConfig.toFarmTime(operation.getActualStartAt()),
				TimeConfig.toFarmTime(operation.getActualEndAt()),
				operation.getSourceScopeType(),
				operation.getSourceScopeId(),
				operation.getSourceConditionSnapshot(),
				TimeConfig.toFarmTime(operation.getTargetSnapshotAt()),
				operation.getDetails(),
				operation.getWorker(),
				operation.getMemo(),
				WorkOperationProgressResponse.from(targets),
				targets,
				availableActions);
	}
}
