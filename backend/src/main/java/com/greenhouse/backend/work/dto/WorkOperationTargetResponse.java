package com.greenhouse.backend.work.dto;

import com.greenhouse.backend.work.application.ResolvedWorkTarget;
import com.greenhouse.backend.work.application.InboundPottingPlanTarget;
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
		return from(target, execution, null);
	}

	public static WorkOperationTargetResponse from(
			WorkOperationTarget target,
			WorkTargetExecution execution,
			InboundPottingPlanTarget currentInbound) {
		String varietyName = currentInbound == null
				? target.getVarietyNameSnapshot()
				: currentInbound.varietyName();
		int quantity = currentInbound == null
				? target.getQuantitySnapshot()
				: currentInbound.currentQuantity(target.getQuantitySnapshot());
		String potSize = currentInbound == null
				? target.getPotSizeSnapshot()
				: currentInbound.potSize();
		Map<String, Object> location = currentInbound == null
				? target.getLocationSnapshot()
				: inboundLocation(currentInbound);
		return new WorkOperationTargetResponse(
				target.getId(),
				target.getTargetReferenceType(),
				target.getOrchidGroupId(),
				target.getInboundRecordId(),
				target.getInclusionSource(),
				varietyName,
				quantity,
				target.getAgeYearSnapshot(),
				target.getPotSizeCodeSnapshot(),
				potSize,
				location,
				execution.getProcessedQuantity(),
				Math.max(0, quantity - execution.getProcessedQuantity()),
				execution.getStatus(),
				execution.getStartedAt(),
				execution.getCompletedAt(),
				execution.getEffectAppliedAt(),
				execution.getWorker(),
				execution.getResultDetails());
	}

	private static Map<String, Object> inboundLocation(InboundPottingPlanTarget inbound) {
		Map<String, Object> location = new java.util.LinkedHashMap<>();
		location.put("tempLocation", inbound.tempLocation());
		location.put("pottingDueDate", inbound.pottingDueDate());
		return location;
	}
}
