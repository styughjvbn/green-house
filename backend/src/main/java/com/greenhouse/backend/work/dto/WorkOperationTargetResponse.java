package com.greenhouse.backend.work.dto;

import com.greenhouse.backend.work.application.ResolvedWorkTarget;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
import com.greenhouse.backend.work.domain.WorkTargetExecution;
import com.greenhouse.backend.work.domain.WorkTargetInclusionSource;
import com.greenhouse.backend.work.domain.WorkTargetExecutionStatus;
import java.time.LocalDateTime;
import java.util.Map;

public record WorkOperationTargetResponse(
		Long id,
		Long orchidGroupId,
		WorkTargetInclusionSource inclusionSource,
		String varietyName,
		Integer quantitySnapshot,
		Integer ageYearSnapshot,
		String potSizeCodeSnapshot,
		String potSizeSnapshot,
		Map<String, Object> locationSnapshot,
		WorkTargetExecutionStatus executionStatus,
		LocalDateTime startedAt,
		LocalDateTime completedAt,
		String worker,
		Map<String, Object> resultDetails) {

	public static WorkOperationTargetResponse preview(
			ResolvedWorkTarget target) {
		return new WorkOperationTargetResponse(
				null,
				target.orchidGroupId(),
				null,
				target.varietyName(),
				target.quantity(),
				target.ageYear(),
				target.potSizeCode(),
				target.potSize(),
				target.location(),
				WorkTargetExecutionStatus.PENDING,
				null,
				null,
				null,
				null);
	}

	public static WorkOperationTargetResponse from(
			WorkOperationTarget target,
			WorkTargetExecution execution) {
		return new WorkOperationTargetResponse(
				target.getId(),
				target.getOrchidGroupId(),
				target.getInclusionSource(),
				target.getVarietyNameSnapshot(),
				target.getQuantitySnapshot(),
				target.getAgeYearSnapshot(),
				target.getPotSizeCodeSnapshot(),
				target.getPotSizeSnapshot(),
				target.getLocationSnapshot(),
				execution.getStatus(),
				execution.getStartedAt(),
				execution.getCompletedAt(),
				execution.getWorker(),
				execution.getResultDetails());
	}
}
