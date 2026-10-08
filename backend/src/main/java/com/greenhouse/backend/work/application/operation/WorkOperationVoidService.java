package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.api.operation.WorkOperationRelationType;
import com.greenhouse.backend.work.api.operation.WorkOperationStatus;
import com.greenhouse.backend.work.api.operation.WorkOperationView;
import com.greenhouse.backend.work.api.operation.WorkTypeWorkflow;
import com.greenhouse.backend.work.api.target.WorkTargetExecutionStatus;
import com.greenhouse.backend.work.api.target.WorkTargetReferenceType;
import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import com.greenhouse.backend.work.dto.operation.WorkOperationBatchCancellationRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationBatchCancellationResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationCancellationEligibilityResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationCancellationRequest;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import com.greenhouse.backend.work.spi.operation.PottingVoidPort;
import com.greenhouse.backend.work.spi.operation.StructureChangeVoidPort;
import com.greenhouse.backend.work.spi.target.InboundPottingPlanGateway;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkOperationVoidService {

  private final WorkOperationRepository operationRepository;

  private final WorkAppliedEffectRepository effectRepository;

  private final WorkTargetExecutionRepository executionRepository;

  private final WorkOperationTargetRepository targetRepository;

  private final StructureChangeVoidPort structureChangeVoidPort;

  private final PottingVoidPort pottingVoidPort;

  private final InboundPottingPlanGateway inboundPottingPlanGateway;

  private final WorkOperationQueryService queryService;

  private final WorkOperationSupport support;

  private final WorkOperationLockService operationLocks;

  @Transactional(readOnly = true)
  public WorkOperationCancellationEligibilityResponse eligibility(Long operationId) {
    var operation =
        operationRepository
            .findWithWorkTypeById(operationId)
            .orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
    return inspectCancellation(operationId, operation, InspectionMode.PREVIEW).toResponse();
  }

  private CancellationInspection inspectCancellation(
      Long operationId, WorkOperation operation, InspectionMode mode) {
    var blockers = operationBlockers(operation);
    if (!blockers.isEmpty()) {
      return new CancellationInspection(operation, List.of(), List.of(), List.of(), blockers);
    }
    var scope = collectCancellationScope(operationId, operation);
    blockers = new ArrayList<>(relatedOperationBlockers(scope.relatedOperations()));
    List<WorkOperationCancellationEligibilityResponse.AffectedOrchidGroup> affectedGroups;
    if (scope.mutationIds().isEmpty()) {
      affectedGroups = recordOnlyAffectedGroups(scope.operationIds());
    } else {
      var inspection = inspectMutations(operationId, operation, scope, mode);
      blockers.addAll(
          inspection.blockers().stream()
              .map(
                  blocker ->
                      new WorkOperationCancellationEligibilityResponse.Blocker(
                          blocker.code(), blocker.message(), blocker.count()))
              .toList());
      affectedGroups = mutationAffectedGroups(inspection);
    }
    return new CancellationInspection(
        operation,
        scope.relatedOperations(),
        scope.mutationIds(),
        affectedGroups,
        List.copyOf(blockers));
  }

  private List<WorkOperationCancellationEligibilityResponse.Blocker> operationBlockers(
      WorkOperation operation) {
    if (operation.getStatus() == WorkOperationStatus.STOPPED
        || operation.getStatus() == WorkOperationStatus.CANCELED
        || operation.getStatus() == WorkOperationStatus.VOIDED) {
      return List.of(
          new WorkOperationCancellationEligibilityResponse.Blocker(
              "ALREADY_CLOSED", "이미 종료되었거나 취소된 작업입니다.", 1));
    } else if (operation.getRelationType() == WorkOperationRelationType.MOVEMENT_DISCARD) {
      return List.of(
          new WorkOperationCancellationEligibilityResponse.Blocker(
              "VOID_WITH_PARENT_MOVEMENT", "이 폐기는 연관된 자리 이동 작업에서 함께 취소해야 합니다.", 1));
    } else if (!cancelableStatus(operation.getStatus())
        || !operation.getWorkType().supportsUserCancellation()) {
      return List.of(
          new WorkOperationCancellationEligibilityResponse.Blocker(
              "UNSUPPORTED_OPERATION", "이 상태와 작업 유형은 취소할 수 없습니다.", 1));
    }
    return List.of();
  }

  private CancellationScope collectCancellationScope(Long operationId, WorkOperation operation) {
    var relatedDiscards =
        operation.getRelationType() == null
            ? operationRepository.findByParentOperationIdAndRelationTypeOrderByIdAsc(
                operationId, WorkOperationRelationType.MOVEMENT_DISCARD)
            : List.<WorkOperation>of();
    List<Long> targetOperationIds = new ArrayList<>();
    targetOperationIds.add(operationId);
    targetOperationIds.addAll(relatedDiscards.stream().map(WorkOperation::getId).toList());
    var effects =
        targetOperationIds.stream()
            .flatMap(id -> effectRepository.findByWorkOperationIdOrderByIdAsc(id).stream())
            .toList();
    List<Long> mutationIds =
        effects.stream()
            .map(effect -> effect.getMutationId())
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();
    return new CancellationScope(
        relatedDiscards, List.copyOf(targetOperationIds), effects, mutationIds);
  }

  private List<WorkOperationCancellationEligibilityResponse.Blocker> relatedOperationBlockers(
      List<WorkOperation> relatedOperations) {
    return relatedOperations.stream()
        .filter(operation -> operation.getStatus() != WorkOperationStatus.COMPLETED)
        .map(
            operation ->
                new WorkOperationCancellationEligibilityResponse.Blocker(
                    "RELATED_DISCARD_NOT_COMPLETED", "연관된 이동 후 잔여 난 폐기 작업의 상태가 완료가 아닙니다.", 1))
        .toList();
  }

  private List<WorkOperationCancellationEligibilityResponse.AffectedOrchidGroup>
      recordOnlyAffectedGroups(List<Long> operationIds) {
    var groups =
        new LinkedHashMap<Long, WorkOperationCancellationEligibilityResponse.AffectedOrchidGroup>();
    targetRepository
        .findByWorkOperationIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(operationIds)
        .stream()
        .filter(target -> target.getTargetReferenceType() == WorkTargetReferenceType.ORCHID_GROUP)
        .forEach(
            target ->
                groups.putIfAbsent(
                    target.getOrchidGroupId(),
                    new WorkOperationCancellationEligibilityResponse.AffectedOrchidGroup(
                        target.getOrchidGroupId(),
                        target.getVarietyNameSnapshot(),
                        target.getQuantitySnapshot(),
                        WorkOperationCancellationEligibilityResponse.ImpactType.RECORD_CANCELED)));
    return List.copyOf(groups.values());
  }

  private StructureChangeVoidPort.Inspection inspectMutations(
      Long operationId, WorkOperation operation, CancellationScope scope, InspectionMode mode) {
    if (operation.getWorkType().workflow() == WorkTypeWorkflow.POTTING) {
      var portEffects = toPottingEffects(scope.effects());
      var inspection =
          mode == InspectionMode.LOCKED
              ? pottingVoidPort.inspectForUpdate(operationId, portEffects)
              : pottingVoidPort.inspect(operationId, portEffects);
      return new StructureChangeVoidPort.Inspection(
          List.of(), inspection.resultOrchidGroups(), inspection.blockers());
    }
    return mode == InspectionMode.LOCKED
        ? structureChangeVoidPort.inspectForUpdate(operationId, scope.mutationIds())
        : structureChangeVoidPort.inspect(operationId, scope.mutationIds());
  }

  private List<PottingVoidPort.Effect> toPottingEffects(List<WorkAppliedEffect> effects) {
    return effects.stream()
        .map(
            effect ->
                new PottingVoidPort.Effect(
                    effect.getTarget() == null ? null : effect.getTarget().getInboundRecordId(),
                    effect.getMutationId()))
        .toList();
  }

  private List<WorkOperationCancellationEligibilityResponse.AffectedOrchidGroup>
      mutationAffectedGroups(StructureChangeVoidPort.Inspection inspection) {
    List<WorkOperationCancellationEligibilityResponse.AffectedOrchidGroup> affectedGroups =
        new ArrayList<>();
    inspection
        .sourceOrchidGroups()
        .forEach(
            group ->
                affectedGroups.add(
                    toAffectedGroup(
                        group, WorkOperationCancellationEligibilityResponse.ImpactType.RESTORED)));
    inspection
        .resultOrchidGroups()
        .forEach(
            group ->
                affectedGroups.add(
                    toAffectedGroup(
                        group,
                        WorkOperationCancellationEligibilityResponse.ImpactType
                            .CREATION_CANCELED)));
    return List.copyOf(affectedGroups);
  }

  private WorkOperationCancellationEligibilityResponse.AffectedOrchidGroup toAffectedGroup(
      StructureChangeVoidPort.OrchidGroupSummary group,
      WorkOperationCancellationEligibilityResponse.ImpactType impactType) {
    return new WorkOperationCancellationEligibilityResponse.AffectedOrchidGroup(
        group.orchidGroupId(), group.varietyName(), group.quantity(), impactType);
  }

  public WorkOperationView voidOperation(
      Long operationId, WorkOperationCancellationRequest request) {
    return cancelOperation(operationId, request);
  }

  public WorkOperationBatchCancellationResponse cancelBatch(
      WorkOperationBatchCancellationRequest request) {
    List<Long> ids = request.workOperationIds().stream().distinct().sorted().toList();
    if (ids.isEmpty() || ids.size() > 100)
      throw new IllegalArgumentException("일괄 취소 작업은 1~100건이어야 합니다.");
    String key = support.normalizeRequired(request.idempotencyKey());
    String reason = support.normalizeRequired(request.reason());
    List<WorkOperation> operations = operationRepository.findAllForUpdateByIdIn(ids);
    if (operations.size() != ids.size()) {
      throw new NotFoundException("일괄 취소할 작업을 찾을 수 없습니다.");
    }
    operationRepository.findByIdIn(ids);
    boolean replayOnly =
        operations.stream()
            .allMatch(
                operation ->
                    operation.getStatus() == WorkOperationStatus.VOIDED
                        && batchRequestKey(key, operation.getId())
                            .equals(operation.getVoidRequestKey()));
    Set<Long> operationIds = Set.copyOf(ids);
    validateBatchOperations(operations, ids, replayOnly);
    var effects = effectRepository.findByWorkOperationIdInOrderByWorkOperationIdAscIdAsc(ids);
    var mutationIds = requireBatchMutationIds(effects, operationIds);
    Long compensationId =
        structureChangeVoidPort.compensateBatch(
            operationIds,
            key,
            mutationIds,
            request.creationCancellationOrchidGroupIds(),
            operations.getFirst().getPlannedStartDate(),
            reason,
            replayOnly);
    if (!replayOnly) {
      var now = support.now();
      effects.forEach(effect -> effect.cancel(now));
      cancelOpenExecutions(executionRepository.findByTargetWorkOperationIdInOrderByIdAsc(ids), now);
      for (var operation : operations) {
        operation.voidCompletedMutationWork(
            now, reason, batchRequestKey(key, operation.getId()), compensationId);
      }
    }
    return new WorkOperationBatchCancellationResponse(
        ids, compensationId, request.creationCancellationOrchidGroupIds());
  }

  private void validateBatchOperations(
      List<WorkOperation> operations, List<Long> ids, boolean replayOnly) {
    if (!replayOnly
        && operations.stream()
            .anyMatch(operation -> operation.getStatus() != WorkOperationStatus.COMPLETED)) {
      throw new IllegalArgumentException("일괄 취소는 완료된 구조 변경·이동·폐기 작업만 지원합니다.");
    }
    Set<Long> operationIds = Set.copyOf(ids);
    for (var operation : operations) {
      var workflow = operation.getWorkType().workflow();
      if (!operation.getWorkType().supportsMutationVoid() || workflow == WorkTypeWorkflow.POTTING) {
        throw new IllegalArgumentException("일괄 취소는 구조 변경·이동·폐기 작업만 지원합니다.");
      }
      if (operation.getRelationType() == WorkOperationRelationType.MOVEMENT_DISCARD
          && (operation.getParentOperation() == null
              || !operationIds.contains(operation.getParentOperation().getId()))) {
        throw new IllegalArgumentException("연관 폐기는 원본 자리 이동 작업과 함께 선택해야 합니다.");
      }
    }
    if (operationRepository.findByParentOperationIdInOrderByParentOperationIdAscIdAsc(ids).stream()
        .filter(item -> item.getRelationType() == WorkOperationRelationType.MOVEMENT_DISCARD)
        .anyMatch(item -> !operationIds.contains(item.getId()))) {
      throw new IllegalArgumentException("자리 이동의 연관 폐기 작업도 함께 선택해야 합니다.");
    }
  }

  private List<Long> requireBatchMutationIds(
      List<WorkAppliedEffect> effects, Set<Long> operationIds) {
    if (effects.stream().anyMatch(effect -> effect.getMutationId() == null)
        || !effects.stream()
            .map(effect -> effect.getWorkOperation().getId())
            .collect(Collectors.toSet())
            .containsAll(operationIds)) {
      throw new IllegalArgumentException("모든 선택 작업에 취소할 Mutation이 있어야 합니다.");
    }
    return effects.stream().map(effect -> effect.getMutationId()).distinct().sorted().toList();
  }

  public WorkOperationView cancelOperation(
      Long operationId, WorkOperationCancellationRequest request) {
    var operation = operationLocks.lock(operationId);
    String requestKey = support.normalizeRequired(request.idempotencyKey());
    String reason = support.normalizeRequired(request.reason());
    if (operation.getStatus() == WorkOperationStatus.CANCELED
        || operation.getStatus() == WorkOperationStatus.VOIDED) {
      if (!requestKey.equals(operation.getVoidRequestKey())) {
        throw new IllegalArgumentException("이미 다른 요청으로 취소된 작업입니다.");
      }
      if (!reason.equals(operation.getVoidReason())) {
        throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "같은 취소 키를 다른 사유에 사용할 수 없습니다.");
      }
      return queryService.get(operationId);
    }
    executionRepository.findForUpdateByTargetWorkOperationIdOrderByIdAsc(operationId);
    var inspection = inspectCancellation(operationId, operation, InspectionMode.LOCKED);
    if (!inspection.cancellable()) {
      throw new IllegalArgumentException(inspection.blockers().getFirst().message());
    }
    var now = support.now();
    var executions = executionRepository.findByTargetWorkOperationIdOrderByIdAsc(operationId);
    if (inspection.mutationIds().isEmpty()) {
      effectRepository
          .findByWorkOperationIdOrderByIdAsc(operationId)
          .forEach(effect -> effect.cancel(now));
      cancelOpenExecutions(executions, now);
      closeInboundPottingPlans(operation, executions);
      operation.cancelRecordedWork(now, reason, requestKey);
      return queryService.get(operationId);
    }
    Long mutationId = compensateOperation(operationId, operation, inspection, requestKey, reason);
    cancelOpenExecutions(executions, now);
    closeInboundPottingPlans(operation, executions);
    cancelRelatedDiscards(operationId, now, reason, requestKey, mutationId);
    effectRepository
        .findByWorkOperationIdOrderByIdAsc(operationId)
        .forEach(effect -> effect.cancel(now));
    operation.voidCompletedMutationWork(now, reason, requestKey, mutationId);
    return queryService.get(operationId);
  }

  private Long compensateOperation(
      Long operationId,
      WorkOperation operation,
      CancellationInspection inspection,
      String requestKey,
      String reason) {
    if (operation.getWorkType().workflow() == WorkTypeWorkflow.POTTING) {
      var effects = effectRepository.findByWorkOperationIdOrderByIdAsc(operationId);
      return pottingVoidPort.compensate(
          operationId,
          requestKey,
          toPottingEffects(effects),
          operation.getPlannedStartDate(),
          reason,
          true);
    }
    return structureChangeVoidPort.compensate(
        operationId, requestKey, inspection.mutationIds(), operation.getPlannedStartDate(), reason);
  }

  private void cancelRelatedDiscards(
      Long operationId, LocalDateTime now, String reason, String requestKey, Long mutationId) {
    List<WorkOperation> relatedDiscards =
        operationRepository.findByParentOperationIdAndRelationTypeOrderByIdAsc(
            operationId, WorkOperationRelationType.MOVEMENT_DISCARD);
    for (int index = relatedDiscards.size() - 1; index >= 0; index--) {
      var discard = relatedDiscards.get(index);
      effectRepository
          .findByWorkOperationIdOrderByIdAsc(discard.getId())
          .forEach(effect -> effect.cancel(now));
      discard.voidCompletedMutationWork(
          now, reason, relatedRequestKey(requestKey, discard.getId()), mutationId);
    }
  }

  private boolean cancelableStatus(WorkOperationStatus status) {
    return status == WorkOperationStatus.PLANNED
        || status == WorkOperationStatus.IN_PROGRESS
        || status == WorkOperationStatus.PAUSED
        || status == WorkOperationStatus.COMPLETED;
  }

  private void cancelOpenExecutions(
      List<WorkTargetExecution> executions, LocalDateTime canceledAt) {
    executions.stream()
        .filter(execution -> execution.getStatus() != WorkTargetExecutionStatus.COMPLETED)
        .filter(execution -> execution.getStatus() != WorkTargetExecutionStatus.SKIPPED)
        .forEach(execution -> execution.cancel(canceledAt));
  }

  private void closeInboundPottingPlans(
      WorkOperation operation, List<WorkTargetExecution> executions) {
    if (operation.getWorkType().workflow() != WorkTypeWorkflow.POTTING) {
      return;
    }
    List<Long> inboundRecordIds =
        executions.stream()
            .filter(execution -> execution.getStatus() != WorkTargetExecutionStatus.COMPLETED)
            .map(WorkTargetExecution::getTarget)
            .filter(
                target -> target.getTargetReferenceType() == WorkTargetReferenceType.INBOUND_RECORD)
            .map(target -> target.getInboundRecordId())
            .distinct()
            .toList();
    if (!inboundRecordIds.isEmpty()) {
      inboundPottingPlanGateway.closePottingPlan(inboundRecordIds);
    }
  }

  public void voidInboundRegistration(Long operationId, WorkOperationCancellationRequest request) {
    var operation = operationLocks.lock(operationId);
    String requestKey = support.normalizeRequired(request.idempotencyKey());
    String reason = support.normalizeRequired(request.reason());
    if (operation.getStatus() == WorkOperationStatus.VOIDED) {
      if (!requestKey.equals(operation.getVoidRequestKey())) {
        throw new IllegalArgumentException("이미 다른 요청으로 무효화된 입고 작업입니다.");
      }
      if (!reason.equals(operation.getVoidReason())) {
        throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "같은 취소 키를 다른 사유에 사용할 수 없습니다.");
      }
      return;
    }
    if (operation.getStatus() != WorkOperationStatus.COMPLETED
        || !WorkTypeDefinition.INBOUND.name().equals(operation.getWorkType().getCode())) {
      throw new IllegalArgumentException("완료된 즉시 배치 입고 작업만 취소할 수 있습니다.");
    }
    var effects = effectRepository.findByWorkOperationIdOrderByIdAsc(operationId);
    var portEffects = toPottingEffects(effects);
    var inspection = pottingVoidPort.inspectForUpdate(operationId, portEffects);
    if (!inspection.blockers().isEmpty()) {
      throw new IllegalArgumentException(inspection.blockers().getFirst().message());
    }
    Long mutationId =
        pottingVoidPort.compensate(
            operationId, requestKey, portEffects, operation.getPlannedStartDate(), reason, false);
    var now = support.now();
    effects.forEach(effect -> effect.cancel(now));
    operation.voidCompletedInboundRegistration(now, reason, requestKey, mutationId);
  }

  private String batchRequestKey(String key, Long operationId) {
    return "batch-"
        + UUID.nameUUIDFromBytes(
            ("BATCH_VOID:" + key + ":" + operationId).getBytes(StandardCharsets.UTF_8));
  }

  private String relatedRequestKey(String requestKey, Long operationId) {
    String suffix = "-related-" + operationId;
    int prefixLength = Math.max(1, 100 - suffix.length());
    return requestKey.substring(0, Math.min(prefixLength, requestKey.length())) + suffix;
  }

  private enum InspectionMode {
    PREVIEW,
    LOCKED
  }

  private record CancellationScope(
      List<WorkOperation> relatedOperations,
      List<Long> operationIds,
      List<WorkAppliedEffect> effects,
      List<Long> mutationIds) {}

  private record CancellationInspection(
      WorkOperation operation,
      List<WorkOperation> relatedOperations,
      List<Long> mutationIds,
      List<WorkOperationCancellationEligibilityResponse.AffectedOrchidGroup> affectedOrchidGroups,
      List<WorkOperationCancellationEligibilityResponse.Blocker> blockers) {

    private boolean cancellable() {
      return blockers.isEmpty();
    }

    private WorkOperationCancellationEligibilityResponse toResponse() {
      List<WorkOperationCancellationEligibilityResponse.AffectedOperation> affectedOperations =
          new ArrayList<>();
      affectedOperations.add(toAffectedOperation(operation, true));
      relatedOperations.forEach(
          related -> affectedOperations.add(toAffectedOperation(related, false)));
      return new WorkOperationCancellationEligibilityResponse(
          operation.getId(),
          cancellable(),
          List.copyOf(affectedOperations),
          affectedOrchidGroups,
          blockers);
    }

    private WorkOperationCancellationEligibilityResponse.AffectedOperation toAffectedOperation(
        WorkOperation item, boolean primary) {
      return new WorkOperationCancellationEligibilityResponse.AffectedOperation(
          item.getId(),
          item.getTitle(),
          item.getWorkType().getName(),
          item.getPlannedStartDate(),
          primary);
    }
  }
}
