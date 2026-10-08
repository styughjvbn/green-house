package com.greenhouse.backend.work.effect.application;

import com.greenhouse.backend.work.api.effect.WorkEffectCommand;
import com.greenhouse.backend.work.api.effect.WorkEffectContext;
import com.greenhouse.backend.work.api.effect.WorkEffectKind;
import com.greenhouse.backend.work.api.effect.WorkExecutionResult;
import com.greenhouse.backend.work.operation.domain.WorkOperation;
import com.greenhouse.backend.work.operation.domain.WorkTypeDefinition;
import com.greenhouse.backend.work.spi.effect.WorkEffectHandler;
import com.greenhouse.backend.work.target.domain.WorkOperationTarget;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WorkEffectProcessor {

  private final Map<String, WorkEffectHandler> handlers;

  private final WorkEffectStore effectStore;

  public WorkEffectProcessor(List<WorkEffectHandler> handlers, WorkEffectStore effectStore) {
    this.handlers = new HashMap<>();
    for (WorkEffectHandler handler : handlers) {
      WorkEffectHandler duplicate = this.handlers.put(handler.supports(), handler);
      if (duplicate != null) {
        throw new IllegalStateException("작업 효과 handler code는 중복될 수 없습니다.");
      }
    }
    this.effectStore = effectStore;
  }

  @PostConstruct
  void validateDefinitions() {
    for (String code : WorkTypeDefinition.requiredHandlerCodes()) {
      if (!handlers.containsKey(code)) {
        throw new IllegalStateException("작업 정의에 필요한 효과 handler가 없습니다: " + code);
      }
    }
  }

  public WorkExecutionResult apply(
      WorkOperation operation, WorkOperationTarget target, WorkEffectCommand command) {
    return apply(
        operation,
        target,
        command,
        target == null ? "OPERATION" : "TARGET:" + target.getId(),
        target != null && target.getOrchidGroupId() != null
            ? List.of(target.getOrchidGroupId())
            : List.of());
  }

  public WorkExecutionResult applyNew(
      WorkOperation operation, WorkOperationTarget target, WorkEffectCommand command) {
    return executeAndPersist(
        operation,
        target,
        command,
        "TARGET:" + target.getId(),
        target.getOrchidGroupId() == null ? List.of() : List.of(target.getOrchidGroupId()));
  }

  public WorkExecutionResult applyBatch(
      WorkOperation operation,
      String executionKey,
      List<Long> sourceOrchidGroupIds,
      WorkEffectCommand command) {
    return apply(operation, null, command, "EXECUTION:" + executionKey, sourceOrchidGroupIds);
  }

  public WorkExecutionResult applyTargetExecution(
      WorkOperation operation,
      WorkOperationTarget target,
      String executionKey,
      WorkEffectCommand command) {
    return apply(
        operation,
        target,
        command,
        "POTTING:" + executionKey,
        target.getOrchidGroupId() == null ? List.of() : List.of(target.getOrchidGroupId()));
  }

  private WorkExecutionResult apply(
      WorkOperation operation,
      WorkOperationTarget target,
      WorkEffectCommand command,
      String effectKey,
      List<Long> sourceOrchidGroupIds) {
    var existing = effectStore.find(operation.getId(), effectKey, command);
    if (existing.isPresent()) {
      return existing.get();
    }
    return executeAndPersist(operation, target, command, effectKey, sourceOrchidGroupIds);
  }

  private WorkExecutionResult executeAndPersist(
      WorkOperation operation,
      WorkOperationTarget target,
      WorkEffectCommand command,
      String effectKey,
      List<Long> sourceOrchidGroupIds) {
    String handlerCode = operation.getWorkType().handlerCode();
    WorkEffectHandler handler = handlers.get(handlerCode);
    if (handler == null) {
      throw new IllegalArgumentException("아직 지원하지 않는 작업 효과 handler입니다: " + handlerCode);
    }
    WorkEffectKind effectKind = handler.effectKind();

    WorkEffectCommand routedCommand = command.withEffectKey(effectKey);
    var context = contextFrom(operation, target);
    WorkExecutionResult result = handler.execute(context, routedCommand);
    var definition = WorkTypeDefinition.forCode(context.workTypeCode());
    if (definition.supportsStructureExecution()
        && WorkTypeDefinition.forStoredStructureHandler(result.handlerCode()).orElse(null)
            != definition) {
      throw new IllegalStateException(
          "구조 변경 결과 handler와 작업 정의가 일치하지 않습니다: " + result.handlerCode());
    }
    return effectStore.save(
        operation, target, routedCommand, effectKey, sourceOrchidGroupIds, effectKind, result);
  }

  private static WorkEffectContext contextFrom(
      WorkOperation operation, WorkOperationTarget target) {
    return new WorkEffectContext(
        operation.getId(),
        operation.getWorkType().getCode(),
        operation.getPlannedStartDate(),
        operation.getMemo(),
        target == null
            ? null
            : new WorkEffectContext.Target(
                target.getTargetReferenceType(),
                target.getOrchidGroupId(),
                target.getInboundRecordId(),
                target.getLocationSnapshot()));
  }
}
