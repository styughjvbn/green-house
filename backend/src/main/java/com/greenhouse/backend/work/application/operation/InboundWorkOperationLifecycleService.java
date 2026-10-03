package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import com.greenhouse.backend.work.domain.target.WorkTargetExecutionStatus;
import com.greenhouse.backend.work.dto.operation.WorkOperationCancellationRequest;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

	private final WorkOperationVoidService workOperationVoidService;

	private final WorkOperationLockService operationLocks;

	public void lockForInboundChange(Long inboundRecordId) {
		operationLocks.lockAll(workTargetExecutionRepository.findOperationIdsForInbound(inboundRecordId));
	}

	@Transactional(readOnly = true)
	public Set<Long> findInboundIdsWithUndoablePotting(Collection<Long> inboundIds) {
		if (inboundIds.isEmpty())
			return Set.of();
		return Set.copyOf(workTargetExecutionRepository.findInboundIdsWithAppliedOperation(inboundIds,
				WorkTypeDefinition.POTTING.name(), pottingUndoStatuses()));
	}

	private Set<WorkOperationStatus> pottingUndoStatuses() {
		return Set.of(WorkOperationStatus.COMPLETED, WorkOperationStatus.IN_PROGRESS,
				WorkOperationStatus.PAUSED);
	}

	public Long voidPottingForInboundRecord(Long inboundRecordId, String requestKey, String reason) {
		WorkOperation operation = findSingleCompletedOperation(inboundRecordId, WorkTypeDefinition.POTTING);
		workOperationVoidService.voidOperation(operation.getId(),
				new WorkOperationCancellationRequest(requestKey, reason));
		return operation.getId();
	}

	public void voidInboundRegistrationForCancellation(Long inboundRecordId, String requestKey, String reason) {
		WorkOperation operation = findSingleCompletedOperation(inboundRecordId, WorkTypeDefinition.INBOUND);
		workOperationVoidService.voidInboundRegistration(operation.getId(),
				new WorkOperationCancellationRequest(requestKey, reason));
	}

	public void cancelForInboundRecord(Long inboundRecordId) {
		operationLocks.lockAll(workTargetExecutionRepository.findOperationIdsForInbound(inboundRecordId));
		List<WorkTargetExecution> linkedExecutions = workTargetExecutionRepository
			.findForUpdateByTargetInboundRecordIdOrderByIdAsc(inboundRecordId);
		LocalDateTime canceledAt = support.now();
		Map<Long, List<WorkTargetExecution>> byOperationId = linkedExecutions.stream()
			.filter(execution -> isInboundLifecycleWork(execution.getTarget().getWorkOperation()))
			.collect(Collectors.groupingBy(execution -> execution.getTarget().getWorkOperation().getId()));
		for (List<WorkTargetExecution> executions : byOperationId.values()) {
			WorkOperation operation = executions.getFirst().getTarget().getWorkOperation();
			if (WorkTypeDefinition.INBOUND.name().equals(operation.getWorkType().getCode())) {
				cancelInboundRecordOperation(operation, canceledAt);
			}
			else {
				cancelPottingTarget(operation, executions.getFirst(), canceledAt);
			}
		}
	}

	private void cancelInboundRecordOperation(WorkOperation operation, LocalDateTime canceledAt) {
		if (operation.getStatus() == WorkOperationStatus.CANCELED
				|| operation.getStatus() == WorkOperationStatus.VOIDED) {
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

	private WorkOperation findSingleCompletedOperation(Long inboundRecordId, WorkTypeDefinition definition) {
		var statuses = definition == WorkTypeDefinition.POTTING ? pottingUndoStatuses()
				: Set.of(WorkOperationStatus.COMPLETED);
		List<WorkOperation> operations = workTargetExecutionRepository
			.findAppliedOperationIdsForInbound(inboundRecordId, definition.name(), statuses)
			.stream()
			.map(operationLocks::lock)
			.toList();
		if (operations.isEmpty()) {
			throw new IllegalArgumentException(
					definition == WorkTypeDefinition.POTTING ? "취소할 완료 포트 작업을 찾을 수 없습니다." : "취소할 완료 입고 작업을 찾을 수 없습니다.");
		}
		if (operations.size() > 1) {
			throw new IllegalStateException(
					definition == WorkTypeDefinition.POTTING ? "취소되지 않은 완료 포트 작업이 여러 건입니다." : "완료 입고 작업이 여러 건입니다.");
		}
		return operations.getFirst();
	}

	private void cancelPottingTarget(WorkOperation operation, WorkTargetExecution linkedExecution,
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
		boolean allClosed = allExecutions.stream()
			.allMatch(execution -> execution.getStatus() == WorkTargetExecutionStatus.CANCELED
					|| execution.getStatus() == WorkTargetExecutionStatus.SKIPPED);
		if (!hasCompletedTarget && allClosed) {
			operation.cancel(canceledAt);
		}
	}

	private boolean isInboundLifecycleWork(WorkOperation operation) {
		return WorkTypeDefinition.INBOUND.name().equals(operation.getWorkType().getCode())
				|| WorkTypeDefinition.POTTING.name().equals(operation.getWorkType().getCode());
	}

	private boolean isActive(WorkOperationStatus status) {
		return status == WorkOperationStatus.PLANNED || status == WorkOperationStatus.IN_PROGRESS
				|| status == WorkOperationStatus.PAUSED;
	}

}
