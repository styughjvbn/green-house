package com.greenhouse.backend.work.effect.application;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.work.api.effect.WorkEffectCommand;
import com.greenhouse.backend.work.api.effect.WorkEffectKind;
import com.greenhouse.backend.work.api.effect.WorkExecutionResult;
import com.greenhouse.backend.work.api.effect.WorkMutationLink;
import com.greenhouse.backend.work.effect.domain.WorkAppliedEffect;
import com.greenhouse.backend.work.effect.domain.WorkEffectOrchidGroup;
import com.greenhouse.backend.work.effect.domain.WorkEffectOrchidGroupRelationType;
import com.greenhouse.backend.work.effect.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.effect.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.operation.application.WorkRequestFingerprint;
import com.greenhouse.backend.work.operation.domain.WorkOperation;
import com.greenhouse.backend.work.target.domain.WorkOperationTarget;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkEffectStore {

  private final WorkAppliedEffectRepository appliedEffectRepository;

  private final WorkEffectOrchidGroupRepository effectOrchidGroupRepository;

  private final WorkRequestFingerprint fingerprints;

  public Optional<WorkExecutionResult> find(
      Long operationId, String effectKey, WorkEffectCommand command) {
    return appliedEffectRepository
        .findByWorkOperationIdAndEffectKey(operationId, effectKey)
        .map(
            effect -> {
              validateReplay(effect, command);
              return toResult(effect);
            });
  }

  public void validateReplay(WorkAppliedEffect effect, WorkEffectCommand command) {
    Long targetId = effect.getTarget() == null ? null : effect.getTarget().getId();
    String expected =
        effect.getCommandFingerprint() == null
            ? fingerprints.calculate(
                new EffectRequest(
                    effect.getHandlerCode(),
                    targetId,
                    TimeConfig.toFarmTime(effect.getAppliedAt()).toLocalDate(),
                    effect.getWorker(),
                    effect.getCommandDetails()))
            : effect.getCommandFingerprint();
    if (!expected.equals(fingerprint(effect.getHandlerCode(), targetId, command))) {
      throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "같은 효과 키를 다른 작업 내용에 사용할 수 없습니다.");
    }
  }

  public WorkExecutionResult save(
      WorkOperation operation,
      WorkOperationTarget target,
      WorkEffectCommand command,
      String effectKey,
      List<Long> sourceOrchidGroupIds,
      WorkEffectKind effectKind,
      WorkExecutionResult result) {
    WorkAppliedEffect appliedEffect =
        new WorkAppliedEffect(
            operation,
            target,
            effectKey,
            effectKind,
            result.handlerCode(),
            command.executedAt(),
            command.worker(),
            command.resultDetails(),
            result.storedDetails());
    appliedEffect.recordFingerprint(
        fingerprint(result.handlerCode(), target == null ? null : target.getId(), command));
    if (result.mutationLink() != null) {
      appliedEffect.linkMutation(
          result.mutationLink().mutationId(), result.mutationLink().correlationId());
    }
    appliedEffectRepository.save(appliedEffect);
    var groupLinks = new ArrayList<WorkEffectOrchidGroup>();
    if (effectKind == WorkEffectKind.STRUCTURE_CHANGE || effectKey.startsWith("EXECUTION:")) {
      sourceOrchidGroupIds.stream()
          .distinct()
          .forEach(
              groupId ->
                  groupLinks.add(
                      new WorkEffectOrchidGroup(
                          appliedEffect, groupId, WorkEffectOrchidGroupRelationType.SOURCE)));
    }
    result
        .resultOrchidGroupIds()
        .forEach(
            groupId ->
                groupLinks.add(
                    new WorkEffectOrchidGroup(
                        appliedEffect, groupId, WorkEffectOrchidGroupRelationType.RESULT)));
    effectOrchidGroupRepository.saveAll(groupLinks);
    return result;
  }

  private String fingerprint(String handlerCode, Long targetId, WorkEffectCommand command) {
    return fingerprints.calculate(
        new EffectRequest(
            handlerCode,
            targetId,
            TimeConfig.toFarmTime(command.executedAt()).toLocalDate(),
            command.worker(),
            command.resultDetails()));
  }

  private record EffectRequest(
      String handlerCode,
      Long targetId,
      LocalDate businessDate,
      String worker,
      Map<String, Object> details) {}

  private WorkExecutionResult toResult(WorkAppliedEffect effect) {
    List<Long> resultIds =
        effectOrchidGroupRepository.findByWorkAppliedEffectIdOrderByIdAsc(effect.getId()).stream()
            .filter(link -> link.getRelationType() != WorkEffectOrchidGroupRelationType.SOURCE)
            .map(WorkEffectOrchidGroup::getOrchidGroupId)
            .toList();
    WorkMutationLink mutationLink =
        effect.getMutationId() == null && effect.getCorrelationId() == null
            ? null
            : new WorkMutationLink(effect.getMutationId(), effect.getCorrelationId());
    return WorkExecutionResult.fromStored(
        effect.getHandlerCode(), effect.getResultDetails(), resultIds, mutationLink);
  }
}
