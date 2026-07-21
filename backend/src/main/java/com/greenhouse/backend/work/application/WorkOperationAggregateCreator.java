package com.greenhouse.backend.work.application;

import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
import com.greenhouse.backend.work.domain.WorkTargetExecution;
import com.greenhouse.backend.work.domain.WorkTargetInclusionSource;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkOperationAggregateCreator {

	private final WorkOperationRepository operationRepository;
	private final WorkOperationTargetRepository targetRepository;
	private final WorkTargetExecutionRepository executionRepository;
	private final WorkOperationSupport support;

	public WorkOperation createForOrchidGroups(
			WorkOperation operation,
			List<ResolvedWorkTarget> resolvedTargets,
			WorkTargetInclusionSource inclusionSource,
			Long inclusionSourceId) {
		operationRepository.save(operation);
		var includedAt = support.now();
		List<WorkOperationTarget> targets = targetRepository.saveAll(resolvedTargets.stream()
				.map(group -> new WorkOperationTarget(
						operation,
						group.orchidGroupId(),
						inclusionSource,
						inclusionSourceId,
						group.varietyId(),
						group.varietyName(),
						group.ageYear(),
						group.potSizeCode(),
						group.potSize(),
						group.quantity(),
						group.location(),
						includedAt))
				.toList());
		executionRepository.saveAll(targets.stream().map(WorkTargetExecution::new).toList());
		return operation;
	}

	public WorkOperation createForInboundRecords(
			WorkOperation operation,
			List<InboundPottingPlanTarget> records) {
		operationRepository.save(operation);
		var includedAt = support.now();
		List<WorkOperationTarget> targets = targetRepository.saveAll(records.stream()
				.map(record -> WorkOperationTarget.inboundRecord(
						operation,
						record.id(),
						record.varietyId(),
						record.varietyName(),
						record.currentQuantity(0),
						record.potSize(),
						inboundLocation(record),
						includedAt))
				.toList());
		executionRepository.saveAll(targets.stream().map(WorkTargetExecution::new).toList());
		return operation;
	}

	private java.util.Map<String, Object> inboundLocation(InboundPottingPlanTarget inbound) {
		java.util.Map<String, Object> location = new java.util.LinkedHashMap<>();
		location.put("tempLocation", inbound.tempLocation());
		location.put("pottingDueDate", inbound.pottingDueDate());
		return location;
	}
}
