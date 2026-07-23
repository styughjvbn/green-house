package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import com.greenhouse.backend.work.domain.target.WorkTargetExecutionStatus;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class InboundWorkOperationLifecycleService {

	private final WorkTargetExecutionRepository workTargetExecutionRepository;
	private final WorkAppliedEffectRepository workAppliedEffectRepository;
	private final WorkOperationSupport support;

	public void cancelForInboundRecord(Long inboundRecordId) {
		List<WorkTargetExecution> linkedExecutions = workTargetExecutionRepository
				.findForUpdateByTargetInboundRecordIdOrderByIdAsc(inboundRecordId);
		LocalDateTime canceledAt = support.now();
		Map<Long, List<WorkTargetExecution>> byOperationId = linkedExecutions.stream()
				.filter(execution -> isInboundLifecycleWork(execution.getTarget().getWorkOperation()))
				.collect(Collectors.groupingBy(
						execution -> execution.getTarget().getWorkOperation().getId()));
		for (List<WorkTargetExecution> executions : byOperationId.values()) {
			WorkOperation operation = executions.getFirst().getTarget().getWorkOperation();
			if (WorkType.INBOUND_CODE.equals(operation.getWorkType().getCode())) {
				cancelInboundRecordOperation(operation, canceledAt);
			} else {
				cancelPottingTarget(operation, executions.getFirst(), canceledAt);
			}
		}
	}

	private void cancelInboundRecordOperation(WorkOperation operation, LocalDateTime canceledAt) {
		if (operation.getStatus() == WorkOperationStatus.CANCELED) {
			return;
		}
		if (operation.getStatus() == WorkOperationStatus.COMPLETED) {
			operation.cancelCompletedInbound(canceledAt);
			workAppliedEffectRepository.findByWorkOperationIdOrderByIdAsc(operation.getId())
					.forEach(effect -> effect.cancel(canceledAt));
			return;
		}
		operation.cancel(canceledAt);
	}

	private void cancelPottingTarget(
			WorkOperation operation,
			WorkTargetExecution linkedExecution,
			LocalDateTime canceledAt) {
		if (!isActive(operation.getStatus())) {
			return;
		}
		if (linkedExecution.getStatus() != WorkTargetExecutionStatus.COMPLETED
				&& linkedExecution.getStatus() != WorkTargetExecutionStatus.SKIPPED
				&& linkedExecution.getStatus() != WorkTargetExecutionStatus.CANCELED) {
			linkedExecution.cancel(canceledAt);
		}
		List<WorkTargetExecution> allExecutions = workTargetExecutionRepository
				.findForUpdateByTargetWorkOperationIdOrderByIdAsc(operation.getId());
		boolean hasCompletedTarget = allExecutions.stream()
				.anyMatch(execution -> execution.getStatus() == WorkTargetExecutionStatus.COMPLETED);
		boolean allClosed = allExecutions.stream().allMatch(execution ->
				execution.getStatus() == WorkTargetExecutionStatus.CANCELED
						|| execution.getStatus() == WorkTargetExecutionStatus.SKIPPED);
		if (!hasCompletedTarget && allClosed) {
			operation.cancel(canceledAt);
		}
	}

	private boolean isInboundLifecycleWork(WorkOperation operation) {
		return WorkType.INBOUND_CODE.equals(operation.getWorkType().getCode())
				|| WorkType.POTTING_CODE.equals(operation.getWorkType().getCode());
	}

	private boolean isActive(WorkOperationStatus status) {
		return status == WorkOperationStatus.PLANNED
				|| status == WorkOperationStatus.IN_PROGRESS
				|| status == WorkOperationStatus.PAUSED;
	}
}
