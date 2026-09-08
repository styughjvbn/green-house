package com.greenhouse.backend.work.application.target;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.work.application.target.InboundPottingPlanTarget;
import com.greenhouse.backend.work.application.target.ResolvedWorkTarget;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import com.greenhouse.backend.work.domain.target.WorkTargetAction;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import com.greenhouse.backend.work.domain.target.WorkTargetExecutionStatus;
import com.greenhouse.backend.work.domain.target.WorkTargetInclusionSource;
import com.greenhouse.backend.work.domain.target.WorkTargetReferenceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Schema(name = "WorkOperationTargetResponse")
public record WorkOperationTargetView(Long id, WorkTargetReferenceType targetReferenceType, Long orchidGroupId,
		Long inboundRecordId, WorkTargetInclusionSource inclusionSource, String varietyName, Integer quantitySnapshot,
		Integer ageYearSnapshot, String potSizeCodeSnapshot, String potSizeSnapshot,
		Map<String, Object> locationSnapshot, Integer processedQuantity, Integer remainingQuantity,
		WorkTargetExecutionStatus executionStatus, LocalDateTime startedAt, LocalDateTime completedAt,
		LocalDateTime effectAppliedAt, String worker, Map<String, Object> resultDetails,
		List<Long> resultOrchidGroupIds, List<WorkTargetAction> availableActions) {

	public static WorkOperationTargetView preview(ResolvedWorkTarget target) {
		return new WorkOperationTargetView(null, WorkTargetReferenceType.ORCHID_GROUP, target.orchidGroupId(), null,
				null, target.varietyName(), target.quantity(), target.ageYear(), target.potSizeCode(), target.potSize(),
				target.location(), 0, target.quantity(), WorkTargetExecutionStatus.PENDING, null, null, null, null,
				null, List.of(), List.of());
	}

	public static WorkOperationTargetView from(WorkOperationTarget target, WorkTargetExecution execution) {
		return from(target, execution, null, List.of());
	}

	public static WorkOperationTargetView from(WorkOperationTarget target, WorkTargetExecution execution,
			InboundPottingPlanTarget currentInbound, List<WorkTargetAction> availableActions) {
		String varietyName = currentInbound == null ? target.getVarietyNameSnapshot() : currentInbound.varietyName();
		int quantity = currentInbound == null ? target.getQuantitySnapshot()
				: currentInbound.currentQuantity(target.getQuantitySnapshot());
		String potSize = currentInbound == null ? target.getPotSizeSnapshot() : currentInbound.potSize();
		Map<String, Object> location = currentInbound == null ? target.getLocationSnapshot()
				: inboundLocation(currentInbound);
		return new WorkOperationTargetView(target.getId(), target.getTargetReferenceType(), target.getOrchidGroupId(),
				target.getInboundRecordId(), target.getInclusionSource(), varietyName, quantity,
				target.getAgeYearSnapshot(), target.getPotSizeCodeSnapshot(), potSize, location,
				execution.getProcessedQuantity(), Math.max(0, quantity - execution.getProcessedQuantity()),
				execution.getStatus(), TimeConfig.toFarmTime(execution.getStartedAt()),
				TimeConfig.toFarmTime(execution.getCompletedAt()),
				TimeConfig.toFarmTime(execution.getEffectAppliedAt()), execution.getWorker(),
				execution.getResultDetails(), resultOrchidGroupIds(execution.getResultDetails()), availableActions);
	}

	private static List<Long> resultOrchidGroupIds(Map<String, Object> details) {
		if (details == null || details.isEmpty()) {
			return List.of();
		}
		var ids = new LinkedHashSet<Long>();
		addLong(ids, details.get("resultOrchidGroupId"));
		addLongs(ids, details.get("resultOrchidGroupIds"));
		addLongs(ids, details.get("createdOrchidGroupIds"));
		if (details.get("results") instanceof List<?> results) {
			for (Object result : results) {
				if (result instanceof Map<?, ?> row) {
					addLong(ids, row.get("orchidGroupId"));
				}
			}
		}
		return new ArrayList<>(ids);
	}

	private static void addLongs(LinkedHashSet<Long> ids, Object value) {
		if (value instanceof List<?> values) {
			values.forEach(item -> addLong(ids, item));
		}
	}

	private static void addLong(LinkedHashSet<Long> ids, Object value) {
		if (value instanceof Number number) {
			ids.add(number.longValue());
		}
	}

	private static Map<String, Object> inboundLocation(InboundPottingPlanTarget inbound) {
		Map<String, Object> location = new java.util.LinkedHashMap<>();
		location.put("tempLocation", inbound.tempLocation());
		location.put("pottingDueDate", inbound.pottingDueDate());
		return location;
	}
}
