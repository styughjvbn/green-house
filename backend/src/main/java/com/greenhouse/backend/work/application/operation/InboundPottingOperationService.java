package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.work.api.effect.InboundPottingCommand;
import com.greenhouse.backend.work.application.effect.InboundPottingCommandCodec;
import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import com.greenhouse.backend.work.dto.effect.InboundPottingPlanBatchCreateRequest;
import com.greenhouse.backend.work.dto.effect.InboundPottingPlanCreateRequest;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import com.greenhouse.backend.work.spi.operation.InboundPottingVoidPort;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class InboundPottingOperationService {

  private final InboundPottingPlanService planService;

  private final WorkCommandReceipts receipts;

  private final WorkRequestFingerprint fingerprints;

  private final WorkOperationProgressService progressService;

  private final WorkOperationQueryService queryService;

  private final WorkTargetExecutionRepository workTargetExecutionRepository;

  private final WorkAppliedEffectRepository workAppliedEffectRepository;

  private final WorkOperationLockService operationLocks;

  private final InboundPottingCommandCodec commandCodec;

  private final InboundPottingVoidPort voidPort;

  @Transactional(propagation = Propagation.MANDATORY)
  public void voidForInbound(Long inboundRecordId, String idempotencyKey, String reason) {
    String requestKey = WorkCommandReceipts.normalizeKey(idempotencyKey);
    String normalizedReason = reason == null || reason.trim().isEmpty() ? null : reason.trim();
    receipts.executeExisting(
        "INBOUND_POTTING_VOID:" + inboundRecordId,
        requestKey,
        new PottingVoidIdentity(inboundRecordId, normalizedReason),
        () -> List.of(voidPort.voidPotting(inboundRecordId, requestKey, normalizedReason)));
  }

  // Persisted fingerprint fields are kept separate from retry key and execution metadata.
  private record PottingVoidIdentity(Long inboundRecordId, String reason) {}

  public WorkOperationView executeNow(InboundPottingCommand request) {
    var ids =
        receipts.executeExisting(
            "POTTING:" + request.inboundRecordId(),
            request.idempotencyKey(),
            request,
            () -> {
              operationLocks.lockInboundPlans(List.of(request.inboundRecordId()));
              return List.of(
                  findExistingOperationId(request)
                      .orElseGet(() -> executeActiveOrNewPlan(request)));
            });
    return queryService.get(ids.getFirst());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  List<Long> executeRecord(
      InboundPottingPlanCreateRequest plan, List<InboundPottingCommand> executions) {
    List<Long> inboundRecordIds =
        executions.stream()
            .map(InboundPottingCommand::inboundRecordId)
            .distinct()
            .sorted()
            .toList();
    operationLocks.lockInboundPlans(inboundRecordIds);

    Map<Long, Long> existingOperationIds = findExistingOperationIds(executions);
    List<Long> pendingIds =
        inboundRecordIds.stream().filter(id -> !existingOperationIds.containsKey(id)).toList();
    Map<Long, WorkTargetExecution> activeExecutions = prepareActiveExecutions(plan, pendingIds);

    LinkedHashSet<Long> operationIds = new LinkedHashSet<>();
    for (InboundPottingCommand request : executions) {
      Long existingOperationId = existingOperationIds.get(request.inboundRecordId());
      if (existingOperationId != null) {
        operationIds.add(existingOperationId);
        continue;
      }
      WorkTargetExecution activeExecution = activeExecutions.get(request.inboundRecordId());
      if (activeExecution == null) {
        throw new IllegalStateException("실행할 포트 작업 계획을 찾을 수 없습니다.");
      }
      operationIds.add(executeActivePlan(activeExecution, request));
    }
    return List.copyOf(operationIds);
  }

  private Long executeActiveOrNewPlan(InboundPottingCommand request) {
    Long inboundRecordId = request.inboundRecordId();
    List<WorkTargetExecution> activeExecutions =
        workTargetExecutionRepository.findActiveInboundPottingForUpdate(inboundRecordId);
    if (!activeExecutions.isEmpty()) {
      return executeActivePlan(activeExecutions.getFirst(), request);
    }
    return executeNewPlan(request);
  }

  private Long executeNewPlan(InboundPottingCommand request) {
    Long inboundRecordId = request.inboundRecordId();
    var planned =
        planService.createPlan(
            new InboundPottingPlanCreateRequest(
                "입고 #" + inboundRecordId + " 포트 작업",
                request.pottingDate(),
                request.pottingDate(),
                List.of(inboundRecordId),
                request.worker(),
                request.memo()));
    progressService.startOperation(planned.getId());
    Long targetId =
        workTargetExecutionRepository
            .findByTargetWorkOperationIdOrderByIdAsc(planned.getId())
            .stream()
            .map(WorkTargetExecution::getTarget)
            .filter(target -> inboundRecordId.equals(target.getInboundRecordId()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("포트 작업 대상을 찾을 수 없습니다."))
            .getId();
    return progressService.completePottingTarget(planned.getId(), targetId, request).getId();
  }

  private Optional<Long> findExistingOperationId(InboundPottingCommand request) {
    return workAppliedEffectRepository
        .findInboundPottingEffects(List.of(request.inboundRecordId()), keys(request))
        .stream()
        .findFirst()
        .map(effect -> validatedOperationId(effect, request));
  }

  private Map<Long, Long> findExistingOperationIds(List<InboundPottingCommand> requests) {
    if (requests.isEmpty()) {
      return Map.of();
    }
    List<WorkAppliedEffect> effects =
        workAppliedEffectRepository.findInboundPottingEffects(
            requests.stream().map(InboundPottingCommand::inboundRecordId).distinct().toList(),
            requests.stream().flatMap(request -> keys(request).stream()).distinct().toList());
    Map<Long, Long> operationIds = new LinkedHashMap<>();
    for (InboundPottingCommand request : requests) {
      effects.stream()
          .filter(effect -> matches(effect, request))
          .findFirst()
          .ifPresent(
              effect ->
                  operationIds.put(
                      request.inboundRecordId(), validatedOperationId(effect, request)));
    }
    return operationIds;
  }

  private boolean matches(WorkAppliedEffect effect, InboundPottingCommand request) {
    return request.inboundRecordId().equals(effect.getTarget().getInboundRecordId())
        && keys(request).contains(effect.getEffectKey());
  }

  private Long validatedOperationId(WorkAppliedEffect effect, InboundPottingCommand request) {
    if (!fingerprints
        .calculate(effect.getCommandDetails())
        .equals(fingerprints.calculate(commandCodec.encode(request)))) {
      throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "같은 멱등 키를 다른 포트 작업 요청에 사용할 수 없습니다.");
    }
    return effect.getWorkOperation().getId();
  }

  private Map<Long, WorkTargetExecution> prepareActiveExecutions(
      InboundPottingPlanCreateRequest plan, List<Long> inboundRecordIds) {
    if (inboundRecordIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, WorkTargetExecution> executionsByInboundRecordId =
        indexExecutions(
            workTargetExecutionRepository.findActiveInboundPottingForUpdate(inboundRecordIds));
    List<Long> unplannedIds =
        inboundRecordIds.stream()
            .filter(id -> !executionsByInboundRecordId.containsKey(id))
            .toList();
    if (!unplannedIds.isEmpty()) {
      planService.createBatchPlans(
          new InboundPottingPlanBatchCreateRequest(copyPlan(plan, unplannedIds)));
      indexExecutions(workTargetExecutionRepository.findActiveInboundPottingForUpdate(unplannedIds))
          .forEach(executionsByInboundRecordId::putIfAbsent);
    }
    return executionsByInboundRecordId;
  }

  private Map<Long, WorkTargetExecution> indexExecutions(List<WorkTargetExecution> executions) {
    Map<Long, WorkTargetExecution> indexed = new LinkedHashMap<>();
    executions.forEach(
        execution -> indexed.putIfAbsent(execution.getTarget().getInboundRecordId(), execution));
    return indexed;
  }

  private InboundPottingPlanCreateRequest copyPlan(
      InboundPottingPlanCreateRequest plan, List<Long> inboundRecordIds) {
    return new InboundPottingPlanCreateRequest(
        plan.title(),
        plan.plannedStartDate(),
        plan.plannedEndDate(),
        inboundRecordIds,
        plan.worker(),
        plan.memo());
  }

  private Long executeActivePlan(WorkTargetExecution execution, InboundPottingCommand request) {
    Long operationId = execution.getTarget().getWorkOperation().getId();
    WorkOperationStatus status = execution.getTarget().getWorkOperation().getStatus();
    switch (status) {
      case PLANNED -> progressService.startOperation(operationId);
      case PAUSED -> progressService.resumeOperation(operationId);
      case IN_PROGRESS -> {}
      default -> throw new IllegalStateException("실행할 수 없는 포트 작업 계획입니다.");
    }
    return progressService
        .completePottingTarget(operationId, execution.getTarget().getId(), request)
        .getId();
  }

  private List<String> keys(InboundPottingCommand request) {
    return List.of(
        "POTTING:" + request.inboundRecordId() + ":" + request.idempotencyKey(),
        "POTTING:" + request.idempotencyKey());
  }
}
