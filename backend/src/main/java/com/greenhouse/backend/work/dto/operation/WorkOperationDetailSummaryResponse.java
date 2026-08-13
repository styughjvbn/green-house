package com.greenhouse.backend.work.dto.operation;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record WorkOperationDetailSummaryResponse(
		Long id,
		Long workTypeId,
		String workTypeCode,
		String workType,
		String title,
		WorkOperationStatus status,
		LocalDate plannedStartDate,
		LocalDate plannedEndDate,
		LocalDateTime actualStartAt,
		LocalDateTime actualEndAt,
		String worker,
		String memo) {

	public static WorkOperationDetailSummaryResponse from(WorkOperation operation) {
		return new WorkOperationDetailSummaryResponse(
				operation.getId(),
				operation.getWorkType().getId(),
				operation.getWorkType().getCode(),
				operation.getWorkType().getName(),
				operation.getTitle(),
				operation.getStatus(),
				operation.getPlannedStartDate(),
				operation.getPlannedEndDate(),
				TimeConfig.toFarmTime(operation.getActualStartAt()),
				TimeConfig.toFarmTime(operation.getActualEndAt()),
				operation.getWorker(),
				operation.getMemo());
	}
}
