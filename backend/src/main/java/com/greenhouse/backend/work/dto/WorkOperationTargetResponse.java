package com.greenhouse.backend.work.dto;

import com.greenhouse.backend.work.application.ResolvedWorkTarget;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
import com.greenhouse.backend.work.domain.WorkTargetExecution;
import com.greenhouse.backend.work.domain.WorkTargetInclusionSource;
import com.greenhouse.backend.work.domain.WorkTargetExecutionStatus;
import com.greenhouse.backend.work.domain.WorkTargetReferenceType;
import java.time.LocalDateTime;
import java.util.Map;

public record WorkOperationTargetResponse(
		Long id,
		WorkTargetReferenceType targetReferenceType,
		Long orchidGroupId,
		Long inboundRecordId,
		WorkTargetInclusionSource inclusionSource,
		String varietyName,
		Integer quantitySnapshot,
		Integer ageYearSnapshot,
		String potSizeCodeSnapshot,
		String potSizeSnapshot,
		Map<String, Object> locationSnapshot,
		Integer processedQuantity,
		Integer remainingQuantity,
		WorkTargetExecutionStatus executionStatus,
		LocalDateTime startedAt,
		LocalDateTime completedAt,
		LocalDateTime effectAppliedAt,
		String worker,
		Map<String, Object> resultDetails) {

	public static WorkOperationTargetResponse preview(
			ResolvedWorkTarget target) {
		return new WorkOperationTargetResponse(
				null,
				WorkTargetReferenceType.ORCHID_GROUP,
				target.orchidGroupId(),
				null,
				null,
				target.varietyName(),
				target.quantity(),
				target.ageYear(),
				target.potSizeCode(),
				target.potSize(),
				target.location(),
				0,
				target.quantity(),
				WorkTargetExecutionStatus.PENDING,
				null,
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
				target.getTargetReferenceType(),
				target.getOrchidGroupId(),
				target.getInboundRecordId(),
				target.getInclusionSource(),
				target.getVarietyNameSnapshot(),
				target.getQuantitySnapshot(),
				target.getAgeYearSnapshot(),
				target.getPotSizeCodeSnapshot(),
				target.getPotSizeSnapshot(),
				target.getLocationSnapshot(),
				execution.getProcessedQuantity(),
				Math.max(0, target.getQuantitySnapshot() - execution.getProcessedQuantity()),
				execution.getStatus(),
				execution.getStartedAt(),
				execution.getCompletedAt(),
				execution.getEffectAppliedAt(),
				execution.getWorker(),
				execution.getResultDetails());
	}
}
