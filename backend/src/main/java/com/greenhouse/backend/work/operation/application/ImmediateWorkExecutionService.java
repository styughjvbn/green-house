package com.greenhouse.backend.work.operation.application;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.api.effect.WorkEffectCommand;
import com.greenhouse.backend.work.api.effect.WorkEffectPayload;
import com.greenhouse.backend.work.api.operation.ImmediateWorkExecutionApi;
import com.greenhouse.backend.work.api.operation.WorkOperationView;
import com.greenhouse.backend.work.api.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.api.target.WorkTargetSelection;
import com.greenhouse.backend.work.effect.application.WorkEffectProcessor;
import com.greenhouse.backend.work.effect.domain.WorkEffectOrchidGroupRelationType;
import com.greenhouse.backend.work.effect.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.operation.domain.WorkOperation;
import com.greenhouse.backend.work.operation.domain.WorkTypeDefinition;
import com.greenhouse.backend.work.operation.repository.WorkOperationRepository;
import com.greenhouse.backend.work.spi.target.ResolvedWorkTarget;
import com.greenhouse.backend.work.spi.target.WorkTargetResolver;
import com.greenhouse.backend.work.target.repository.WorkTargetExecutionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ImmediateWorkExecutionService implements ImmediateWorkExecutionApi {

  private final WorkTypeService workTypeService;

  private final WorkEffectProcessor workEffectProcessor;

  private final WorkOperationRepository operationRepository;

  private final WorkEffectOrchidGroupRepository effectOrchidGroupRepository;

  private final WorkTargetResolver workTargetResolver;

  private final WorkOperationAggregateCreator aggregateCreator;

  private final WorkTargetExecutionRepository executionRepository;

  private final WorkOperationQueryService queryService;

  private final WorkOperationSupport support;

  private final WorkCommandReceipts receipts;

  /** Creates the automatic history title from the caller's source variety snapshot. */
  @Override
  public WorkOperationView executeVarietyHistoryForTarget(
      String requestKey,
      String workTypeCode,
      String varietyName,
      LocalDate workDate,
      String worker,
      String memo,
      Long orchidGroupId,
      Map<String, Object> details,
      WorkEffectPayload payload) {
    String title =
        support.varietyHistoryTitle(varietyName, WorkTypeDefinition.forCode(workTypeCode));
    String key = WorkCommandReceipts.normalizeKey(requestKey);
    String actor = support.actor(worker);
    var command =
        new ImmediateCommand(
            workTypeCode, title, workDate, actor, memo, orchidGroupId, details, payload);
    var ids =
        receipts.execute(
            "IMMEDIATE", key, command, () -> List.of(executeNewForTarget(key, command)));
    return queryService.get(ids.getFirst());
  }

  public WorkOperationView execute(
      String requestKey,
      String workTypeCode,
      String title,
      LocalDate workDate,
      String worker,
      String memo,
      Map<String, Object> details,
      WorkEffectPayload payload) {
    String key = WorkCommandReceipts.normalizeKey(requestKey);
    String actor = support.actor(worker);
    var command =
        new ImmediateCommand(workTypeCode, title, workDate, actor, memo, null, details, payload);
    var ids = receipts.execute("IMMEDIATE", key, command, () -> List.of(executeNew(key, command)));
    return queryService.get(ids.getFirst());
  }

  private record ImmediateCommand(
      String workTypeCode,
      String title,
      LocalDate workDate,
      String worker,
      String memo,
      Long orchidGroupId,
      Map<String, Object> details,
      WorkEffectPayload payload) {}

  private Long executeNewForTarget(String requestKey, ImmediateCommand command) {
    if (operationRepository.findByRequestKey(requestKey).isPresent()) {
      throw new ConflictException(
          "IDEMPOTENCY_REPLAY_UNAVAILABLE", "과거 요청 원문이 없어 재실행 내용을 확인할 수 없습니다. 기존 작업을 조회해 주세요.");
    }

    ResolvedWorkTarget resolved = workTargetResolver.getCurrent(command.orchidGroupId());
    WorkTargetSelection targetSelection = WorkTargetSelection.orchidGroup(command.orchidGroupId());
    WorkOperation operation =
        new WorkOperation(
            workTypeService.getByCode(command.workTypeCode()),
            command.title(),
            command.workDate(),
            command.workDate(),
            targetSelection.sourceScopeType(),
            targetSelection.sourceScopeId(),
            targetSelection.conditionSnapshot(),
            command.details(),
            command.worker(),
            command.memo(),
            support.now());
    operation.assignRequestKey(requestKey);
    aggregateCreator.createForOrchidGroups(
        operation,
        List.of(resolved),
        targetSelection.inclusionSource(),
        targetSelection.sourceScopeId());
    var execution =
        executionRepository.findByTargetWorkOperationIdOrderByIdAsc(operation.getId()).getFirst();
    LocalDateTime executedAt = support.now();
    operation.start(executedAt);
    var result =
        workEffectProcessor.apply(
            operation,
            execution.getTarget(),
            new WorkEffectCommand(
                executedAt, command.worker(), command.details(), command.payload()));
    execution.completeWithEffect(executedAt, command.worker(), result.storedDetails());
    operation.complete(executedAt);
    return operation.getId();
  }

  private Long executeNew(String requestKey, ImmediateCommand command) {
    if (operationRepository.findByRequestKey(requestKey).isPresent()) {
      throw new ConflictException(
          "IDEMPOTENCY_REPLAY_UNAVAILABLE", "과거 요청 원문이 없어 재실행 내용을 확인할 수 없습니다. 기존 작업을 조회해 주세요.");
    }

    WorkOperation operation =
        new WorkOperation(
            workTypeService.getByCode(command.workTypeCode()),
            command.title(),
            command.workDate(),
            command.workDate(),
            WorkSourceScopeType.NONE,
            null,
            Map.of(),
            command.details(),
            command.worker(),
            command.memo(),
            support.now());
    operation.assignRequestKey(requestKey);
    operationRepository.save(operation);
    LocalDateTime executedAt = support.now();
    operation.start(executedAt);
    workEffectProcessor.apply(
        operation,
        null,
        new WorkEffectCommand(executedAt, command.worker(), command.details(), command.payload()));
    operation.complete(executedAt);
    return operation.getId();
  }

  @Transactional(readOnly = true)
  @Override
  public List<Long> getStructureChangeResultOrchidGroupIds(Long operationId, String workTypeCode) {
    validateWorkType(operationId, workTypeCode, "요청한 구조 변경 작업 유형과 일치하지 않습니다.");
    return effectOrchidGroupRepository
        .findByWorkAppliedEffectWorkOperationIdAndRelationTypeOrderByIdAsc(
            operationId, WorkEffectOrchidGroupRelationType.RESULT)
        .stream()
        .map(link -> link.getOrchidGroupId())
        .toList();
  }

  private void validateWorkType(Long operationId, String workTypeCode, String message) {
    WorkOperation operation =
        operationRepository
            .findWithWorkTypeById(operationId)
            .orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
    if (!workTypeCode.equals(operation.getWorkType().getCode())) {
      throw new IllegalArgumentException(message);
    }
  }
}
