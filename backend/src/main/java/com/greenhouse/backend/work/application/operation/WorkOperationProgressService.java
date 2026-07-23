package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.application.target.InboundPottingPlanGateway;
import com.greenhouse.backend.work.application.target.InboundPottingPlanTarget;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectProcessor;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import com.greenhouse.backend.work.domain.target.WorkTargetExecutionStatus;
import com.greenhouse.backend.work.domain.target.WorkTargetReferenceType;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.dto.operation.WorkOperationResponse;
import com.greenhouse.backend.work.dto.target.WorkTargetExecutionRequest;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkOperationProgressService {

	private final WorkOperationRepository operationRepository;
	private final WorkTargetExecutionRepository executionRepository;
	private final WorkEffectProcessor workEffectProcessor;
	private final InboundPottingPlanGateway inboundPottingPlanGateway;
	private final WorkOperationQueryService queryService;
	private final WorkOperationSupport support;

	public WorkOperationResponse complete(Long operationId, LocalDate completedDate) {
		WorkOperation operation = findOperation(operationId);
		List<WorkTargetExecution> executions = executionRepository
				.findByTargetWorkOperationIdOrderByIdAsc(operationId);
		if (executions.isEmpty() || executions.stream().anyMatch(execution -> !execution.isTerminalForCompletion())) {
			throw new IllegalArgumentException("모든 작업 대상을 완료하거나 건너뛴 뒤 전체 작업을 완료할 수 있습니다.");
		}
		operation.complete(support.completionTime(completedDate));
		return queryService.get(operationId);
	}

	public WorkOperationResponse start(Long operationId) {
		findOperation(operationId).start(support.now());
		return queryService.get(operationId);
	}

	public WorkOperationResponse pause(Long operationId) {
		findOperation(operationId).pause();
		return queryService.get(operationId);
	}

	public WorkOperationResponse resume(Long operationId) {
		findOperation(operationId).resume();
		return queryService.get(operationId);
	}

	public WorkOperationResponse cancel(Long operationId) {
		WorkOperation operation = findOperation(operationId);
		List<WorkTargetExecution> executions = executionRepository
				.findByTargetWorkOperationIdOrderByIdAsc(operationId);
		LocalDateTime canceledAt = support.now();
		operation.cancel(canceledAt);
		executions.stream()
				.filter(execution -> execution.getStatus() != WorkTargetExecutionStatus.COMPLETED)
				.filter(execution -> execution.getStatus() != WorkTargetExecutionStatus.SKIPPED)
				.forEach(execution -> execution.cancel(canceledAt));
		closeInboundPottingPlans(operation, executions);
		return queryService.get(operationId);
	}

	public WorkOperationResponse startTarget(
			Long operationId, Long targetId, WorkTargetExecutionRequest request) {
		validateOperationInProgress(operationId);
		findExecution(operationId, targetId)
				.start(support.now(), support.normalize(request.worker()));
		return queryService.get(operationId);
	}

	public WorkOperationResponse completeTarget(
			Long operationId, Long targetId, WorkTargetExecutionRequest request) {
		WorkTargetExecution execution = findExecutionForUpdate(operationId, targetId);
		if (execution.isEffectApplied()) {
			return queryService.get(operationId);
		}
		WorkOperation operation = execution.getTarget().getWorkOperation();
		if (operation.getStatus() != WorkOperationStatus.IN_PROGRESS) {
			throw new IllegalArgumentException("진행 중인 작업에서만 대상을 처리할 수 있습니다.");
		}
		refreshInboundSnapshot(operation, execution.getTarget());
		LocalDateTime completedAt = support.completionTime(request.completedDate());
		String worker = support.normalize(request.worker());
		var result = workEffectProcessor.apply(
				operation,
				execution.getTarget(),
				new WorkEffectCommand(completedAt, worker, request.resultDetails(), null));
		execution.completeWithEffect(completedAt, worker, result.resultDetails());
		completeIfAllTargetsClosed(operation, completedAt);
		return queryService.get(operationId);
	}

	public WorkOperationResponse skipTarget(
			Long operationId, Long targetId, WorkTargetExecutionRequest request) {
		validateOperationInProgress(operationId);
		WorkTargetExecution execution = findExecution(operationId, targetId);
		execution.skip(support.now(), support.normalize(request.worker()), request.resultDetails());
		completeIfAllTargetsClosed(execution.getTarget().getWorkOperation(), execution.getCompletedAt());
		closeInboundPottingPlans(execution.getTarget().getWorkOperation(), List.of(execution));
		return queryService.get(operationId);
	}

	void completeIfAllTargetsClosed(WorkOperation operation, LocalDateTime completedAt) {
		if (operation.getStatus() != WorkOperationStatus.IN_PROGRESS) {
			return;
		}
		List<WorkTargetExecution> executions = executionRepository
				.findByTargetWorkOperationIdOrderByIdAsc(operation.getId());
		if (!executions.isEmpty() && executions.stream().allMatch(WorkTargetExecution::isTerminalForCompletion)) {
			operation.complete(completedAt);
		}
	}

	private void refreshInboundSnapshot(WorkOperation operation, WorkOperationTarget target) {
		if (!WorkType.POTTING_CODE.equals(operation.getWorkType().getCode())
				|| target.getTargetReferenceType() != WorkTargetReferenceType.INBOUND_RECORD) {
			return;
		}
		InboundPottingPlanTarget current = inboundPottingPlanGateway
				.findCurrent(List.of(target.getInboundRecordId()))
				.stream()
				.findFirst()
				.orElseThrow(() -> new NotFoundException("포트 작업 대상 입고 기록을 찾을 수 없습니다."));
		target.refreshInboundSnapshot(
				current.varietyId(),
				current.varietyName(),
				current.currentQuantity(target.getQuantitySnapshot()),
				current.potSize(),
				inboundLocation(current));
	}

	private Map<String, Object> inboundLocation(InboundPottingPlanTarget inbound) {
		Map<String, Object> location = new LinkedHashMap<>();
		location.put("tempLocation", inbound.tempLocation());
		location.put("pottingDueDate", inbound.pottingDueDate());
		return location;
	}

	private void closeInboundPottingPlans(
			WorkOperation operation, List<WorkTargetExecution> executions) {
		if (!WorkType.POTTING_CODE.equals(operation.getWorkType().getCode())) {
			return;
		}
		List<Long> inboundRecordIds = executions.stream()
				.filter(execution -> execution.getStatus() != WorkTargetExecutionStatus.COMPLETED)
				.map(WorkTargetExecution::getTarget)
				.filter(target -> target.getTargetReferenceType() == WorkTargetReferenceType.INBOUND_RECORD)
				.map(WorkOperationTarget::getInboundRecordId)
				.distinct()
				.toList();
		if (!inboundRecordIds.isEmpty()) {
			inboundPottingPlanGateway.closePottingPlan(inboundRecordIds);
		}
	}

	private WorkOperation findOperation(Long operationId) {
		return operationRepository.findWithWorkTypeById(operationId)
				.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
	}

	private WorkTargetExecution findExecution(Long operationId, Long targetId) {
		return executionRepository.findByTargetIdAndTargetWorkOperationId(targetId, operationId)
				.orElseThrow(() -> new NotFoundException("작업 대상을 찾을 수 없습니다."));
	}

	private WorkTargetExecution findExecutionForUpdate(Long operationId, Long targetId) {
		return executionRepository.findForUpdateByTargetIdAndTargetWorkOperationId(targetId, operationId)
				.orElseThrow(() -> new NotFoundException("작업 대상을 찾을 수 없습니다."));
	}

	private void validateOperationInProgress(Long operationId) {
		if (findOperation(operationId).getStatus() != WorkOperationStatus.IN_PROGRESS) {
			throw new IllegalArgumentException("진행 중인 작업에서만 대상을 처리할 수 있습니다.");
		}
	}
}
