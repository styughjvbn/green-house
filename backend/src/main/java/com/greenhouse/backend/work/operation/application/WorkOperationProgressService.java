package com.greenhouse.backend.work.operation.application;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.api.effect.InboundPottingCommand;
import com.greenhouse.backend.work.api.effect.WorkEffectCommand;
import com.greenhouse.backend.work.api.operation.WorkOperationStatus;
import com.greenhouse.backend.work.api.operation.WorkOperationView;
import com.greenhouse.backend.work.api.target.WorkTargetExecutionStatus;
import com.greenhouse.backend.work.api.target.WorkTargetReferenceType;
import com.greenhouse.backend.work.effect.application.InboundPottingCommandCodec;
import com.greenhouse.backend.work.effect.application.WorkEffectProcessor;
import com.greenhouse.backend.work.effect.application.WorkEffectStore;
import com.greenhouse.backend.work.effect.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.operation.domain.WorkOperation;
import com.greenhouse.backend.work.operation.domain.WorkTypeDefinition;
import com.greenhouse.backend.work.spi.target.InboundPottingPlanGateway;
import com.greenhouse.backend.work.spi.target.InboundPottingPlanTarget;
import com.greenhouse.backend.work.target.domain.WorkOperationTarget;
import com.greenhouse.backend.work.target.domain.WorkTargetExecution;
import com.greenhouse.backend.work.target.repository.WorkTargetExecutionRepository;
import com.greenhouse.backend.work.target.web.dto.WorkTargetExecutionRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkOperationProgressService {

  private final WorkTargetExecutionRepository executionRepository;

  private final WorkEffectProcessor workEffectProcessor;

  private final WorkEffectStore effectStore;

  private final InboundPottingCommandCodec pottingCommandCodec;

  private final WorkAppliedEffectRepository appliedEffectRepository;

  private final InboundPottingPlanGateway inboundPottingPlanGateway;

  private final WorkOperationQueryService queryService;

  private final WorkOperationSupport support;

  private final WorkOperationLockService operationLocks;

  public WorkOperationView complete(Long operationId, LocalDate completedDate) {
    WorkOperation operation = findOperation(operationId);
    List<WorkTargetExecution> executions =
        executionRepository.findByTargetWorkOperationIdOrderByIdAsc(operationId);
    if (executions.isEmpty()
        || executions.stream().anyMatch(execution -> !execution.isTerminalForCompletion())) {
      throw new IllegalArgumentException("모든 작업 대상을 완료하거나 건너뛴 뒤 전체 작업을 완료할 수 있습니다.");
    }
    operation.complete(support.completionTime(completedDate));
    return queryService.get(operationId);
  }

  public WorkOperationView start(Long operationId) {
    startOperation(operationId);
    return queryService.get(operationId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  void startOperation(Long operationId) {
    findOperation(operationId).start(support.now());
  }

  public WorkOperationView pause(Long operationId) {
    findOperation(operationId).pause();
    return queryService.get(operationId);
  }

  public WorkOperationView resume(Long operationId) {
    resumeOperation(operationId);
    return queryService.get(operationId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  void resumeOperation(Long operationId) {
    findOperation(operationId).resume();
  }

  public WorkOperationView endRemaining(Long operationId) {
    WorkOperation operation = findOperation(operationId);
    List<WorkTargetExecution> executions =
        executionRepository.findByTargetWorkOperationIdOrderByIdAsc(operationId);
    LocalDateTime canceledAt = support.now();
    operation.stop(canceledAt);
    executions.stream()
        .filter(execution -> execution.getStatus() != WorkTargetExecutionStatus.COMPLETED)
        .filter(execution -> execution.getStatus() != WorkTargetExecutionStatus.SKIPPED)
        .forEach(execution -> execution.cancel(canceledAt));
    closeInboundPottingPlans(operation, executions);
    return queryService.get(operationId);
  }

  public WorkOperationView updateTitle(Long operationId, String title) {
    findOperation(operationId).updateTitle(title);
    return queryService.get(operationId);
  }

  public WorkOperationView startTarget(
      Long operationId, Long targetId, WorkTargetExecutionRequest request) {
    validateOperationInProgress(operationId);
    findExecution(operationId, targetId).start(support.now(), support.actor(request.worker()));
    return queryService.get(operationId);
  }

  public WorkOperationView completeTarget(
      Long operationId, Long targetId, WorkTargetExecutionRequest request) {
    completeTargetForRecord(operationId, targetId, request);
    return queryService.get(operationId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  WorkOperation completeTargetForRecord(
      Long operationId, Long targetId, WorkTargetExecutionRequest request) {
    return completeTargetExecution(
        operationId,
        targetId,
        request.completedDate(),
        request.worker(),
        request.resultDetails(),
        null,
        null);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  WorkOperation completePottingTarget(
      Long operationId, Long targetId, InboundPottingCommand request) {
    return completeTargetExecution(
        operationId,
        targetId,
        request.pottingDate(),
        request.worker(),
        pottingCommandCodec.encode(request),
        request,
        request.inboundRecordId() + ":" + request.idempotencyKey());
  }

  private WorkOperation completeTargetExecution(
      Long operationId,
      Long targetId,
      LocalDate completedDate,
      String requestedWorker,
      Map<String, Object> details,
      InboundPottingCommand pottingCommand,
      String executionKey) {
    findOperation(operationId);
    WorkTargetExecution execution = findExecutionForUpdate(operationId, targetId);
    WorkOperation operation = execution.getTarget().getWorkOperation();
    if (execution.isEffectApplied()) {
      String effectKey = executionKey == null ? "TARGET:" + targetId : "POTTING:" + executionKey;
      var existing =
          appliedEffectRepository
              .findByWorkOperationIdAndEffectKey(operationId, effectKey)
              .orElseThrow(
                  () ->
                      new ConflictException(
                          "IDEMPOTENCY_REPLAY_UNAVAILABLE", "완료된 대상은 원래 실행 API로 재요청해야 합니다."));
      var completedAt =
          completedDate == null ? existing.getAppliedAt() : support.completionTime(completedDate);
      effectStore.validateReplay(
          existing,
          new WorkEffectCommand(
              completedAt, support.actor(requestedWorker), details, pottingCommand));
      return operation;
    }
    if (operation.getStatus() != WorkOperationStatus.IN_PROGRESS) {
      throw new IllegalArgumentException("진행 중인 작업에서만 대상을 처리할 수 있습니다.");
    }
    refreshInboundSnapshot(operation, execution.getTarget());
    LocalDateTime completedAt = support.completionTime(completedDate);
    String worker = support.actor(requestedWorker);
    WorkEffectCommand command = new WorkEffectCommand(completedAt, worker, details, pottingCommand);
    var result =
        executionKey == null
            ? workEffectProcessor.apply(operation, execution.getTarget(), command)
            : workEffectProcessor.applyTargetExecution(
                operation, execution.getTarget(), executionKey, command);
    execution.completeWithEffect(completedAt, worker, result.storedDetails());
    completeIfAllTargetsClosed(operation, completedAt);
    return operation;
  }

  public WorkOperationView skipTarget(
      Long operationId, Long targetId, WorkTargetExecutionRequest request) {
    validateOperationInProgress(operationId);
    WorkTargetExecution execution = findExecution(operationId, targetId);
    execution.skip(support.now(), support.actor(request.worker()), request.resultDetails());
    completeIfAllTargetsClosed(
        execution.getTarget().getWorkOperation(), execution.getCompletedAt());
    closeInboundPottingPlans(execution.getTarget().getWorkOperation(), List.of(execution));
    return queryService.get(operationId);
  }

  void completeIfAllTargetsClosed(WorkOperation operation, LocalDateTime completedAt) {
    if (operation.getStatus() != WorkOperationStatus.IN_PROGRESS) {
      return;
    }
    List<WorkTargetExecution> executions =
        executionRepository.findByTargetWorkOperationIdOrderByIdAsc(operation.getId());
    if (!executions.isEmpty()
        && executions.stream().allMatch(WorkTargetExecution::isTerminalForCompletion)) {
      operation.complete(completedAt);
    }
  }

  private void refreshInboundSnapshot(WorkOperation operation, WorkOperationTarget target) {
    if (!WorkTypeDefinition.POTTING.name().equals(operation.getWorkType().getCode())
        || target.getTargetReferenceType() != WorkTargetReferenceType.INBOUND_RECORD) {
      return;
    }
    InboundPottingPlanTarget current =
        inboundPottingPlanGateway.findCurrent(List.of(target.getInboundRecordId())).stream()
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
    if (!WorkTypeDefinition.POTTING.name().equals(operation.getWorkType().getCode())) {
      return;
    }
    List<Long> inboundRecordIds =
        executions.stream()
            .filter(execution -> execution.getStatus() != WorkTargetExecutionStatus.COMPLETED)
            .map(WorkTargetExecution::getTarget)
            .filter(
                target -> target.getTargetReferenceType() == WorkTargetReferenceType.INBOUND_RECORD)
            .map(WorkOperationTarget::getInboundRecordId)
            .distinct()
            .toList();
    if (!inboundRecordIds.isEmpty()) {
      inboundPottingPlanGateway.closePottingPlan(inboundRecordIds);
    }
  }

  private WorkOperation findOperation(Long operationId) {
    return operationLocks.lock(operationId);
  }

  private WorkTargetExecution findExecution(Long operationId, Long targetId) {
    return executionRepository
        .findByTargetIdAndTargetWorkOperationId(targetId, operationId)
        .orElseThrow(() -> new NotFoundException("작업 대상을 찾을 수 없습니다."));
  }

  private WorkTargetExecution findExecutionForUpdate(Long operationId, Long targetId) {
    return executionRepository
        .findForUpdateByTargetIdAndTargetWorkOperationId(targetId, operationId)
        .orElseThrow(() -> new NotFoundException("작업 대상을 찾을 수 없습니다."));
  }

  private void validateOperationInProgress(Long operationId) {
    if (findOperation(operationId).getStatus() != WorkOperationStatus.IN_PROGRESS) {
      throw new IllegalArgumentException("진행 중인 작업에서만 대상을 처리할 수 있습니다.");
    }
  }
}
