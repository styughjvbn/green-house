package com.greenhouse.backend.farm.transformation.integration;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.api.orchid.CancelOrchidGroupCreationMutationCommand;
import com.greenhouse.backend.farm.api.orchid.CorrectOrchidGroupMutationItem;
import com.greenhouse.backend.farm.api.orchid.CorrectOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.api.orchid.RelatedOrchidGroupMutations;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroupStatusPolicy;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidStockCountRepository;
import com.greenhouse.backend.farm.spi.orchid.OrchidGroupUsageInspector;
import com.greenhouse.backend.work.api.correction.OrchidGroupCorrectionInput;
import com.greenhouse.backend.work.api.correction.StructureChangeReferenceApi;
import com.greenhouse.backend.work.api.correction.WorkCorrectionCommand;
import com.greenhouse.backend.work.api.correction.WorkCorrectionQuantityApi;
import com.greenhouse.backend.work.api.effect.WorkEffectResults;
import com.greenhouse.backend.work.api.effect.WorkMutationLink;
import com.greenhouse.backend.work.spi.correction.WorkCorrectionPlan;
import com.greenhouse.backend.work.spi.correction.WorkCorrectionPort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
public class FarmWorkCorrectionAdapter implements WorkCorrectionPort {

  private final StructureChangeReferenceApi structureChangeReferenceReader;

  private final OrchidGroupRepository orchidGroupRepository;

  private final List<OrchidGroupUsageInspector> usageInspectors;

  private final OrchidGroupMutationEngine mutationEngine;

  private final WorkCorrectionQuantityApi quantityService;

  private final OrchidStockCountRepository stockCounts;

  @Override
  public WorkCorrectionPlan prepare(Long originalOperationId, WorkCorrectionCommand request) {
    List<Long> correctableIds =
        structureChangeReferenceReader.getCorrectableResultOrchidGroupIds(originalOperationId);
    Set<Long> adjustmentIds =
        request.orchidGroupAdjustments().stream()
            .map(OrchidGroupCorrectionInput::orchidGroupId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    if (adjustmentIds.size() != request.orchidGroupAdjustments().size()) {
      throw new IllegalArgumentException("같은 난 묶음을 한 보정에서 중복 지정할 수 없습니다.");
    }
    if (request.cancelResultCreation() && adjustmentIds.size() != 1) {
      throw new IllegalArgumentException("결과 생성 취소는 한 번에 하나의 난 묶음만 처리할 수 있습니다.");
    }
    if (!new LinkedHashSet<>(correctableIds).containsAll(adjustmentIds)) {
      throw new IllegalArgumentException("원본 구조 변경 작업이 만든 결과 난 묶음만 보정할 수 있습니다.");
    }
    var quantityChanges = quantityService.validate(originalOperationId, request);
    var context = quantityService.context(originalOperationId);
    var lockIds = new LinkedHashSet<>(adjustmentIds);
    quantityChanges.forEach(change -> lockIds.addAll(change.before().resultQuantities().keySet()));
    Map<Long, OrchidGroup> groupsById =
        orchidGroupRepository.findAllForUpdateByIdIn(lockIds).stream()
            .collect(Collectors.toMap(OrchidGroup::getId, Function.identity()));
    if (groupsById.size() != lockIds.size()) {
      throw new NotFoundException("보정 대상 난 묶음 일부를 찾을 수 없습니다.");
    }
    Set<Long> changedAdjustmentIds = changedAdjustmentIds(request, adjustmentIds, groupsById);
    var changedQuantities =
        request.orchidGroupAdjustments().stream()
            .filter(a -> !groupsById.get(a.orchidGroupId()).getQuantity().equals(a.quantity()))
            .map(OrchidGroupCorrectionInput::orchidGroupId)
            .collect(Collectors.toSet());
    if (!request.cancelResultCreation() && !changedQuantities.isEmpty())
      quantityService.requireEnabled();
    if (!request.cancelResultCreation()
        && !changedQuantities.isEmpty()
        && !context.stream()
            .flatMap(b -> b.resultQuantities().keySet().stream())
            .collect(Collectors.toSet())
            .containsAll(changedQuantities))
      throw new IllegalArgumentException("당시 수량 수지를 확인할 수 없는 결과입니다. 현재 실사 수량 조정을 사용하세요.");
    var guardIds = new LinkedHashSet<>(changedAdjustmentIds);
    quantityChanges.forEach(change -> guardIds.addAll(change.before().resultQuantities().keySet()));
    if (!guardIds.isEmpty()) {
      var reference =
          structureChangeReferenceReader.getMutationReferences(originalOperationId, guardIds);
      boolean countedAfter =
          reference.legacySource()
              ? stockCounts.existsByOrchidGroupIdInAndMutationIdIsNotNull(guardIds)
              : stockCounts.existsAfterMutations(guardIds, reference.mutationIds());
      if (countedAfter)
        throw new ConflictException(
            "WORK_CORRECTION_AFTER_STOCK_COUNT", "실사 이후의 현재 수량을 과거 작업 보정으로 덮을 수 없습니다.");
    }
    var blockers =
        usageInspectors.stream()
            .flatMap(inspector -> inspector.inspect(guardIds, originalOperationId).stream())
            .toList();
    if (!blockers.isEmpty()) {
      throw new IllegalArgumentException(blockers.getFirst().message());
    }
    for (var change : quantityChanges) {
      for (var result : change.before().resultQuantities().entrySet()) {
        if (!groupsById.get(result.getKey()).getQuantity().equals(result.getValue()))
          throw new ConflictException(
              "WORK_CORRECTION_STALE", "현재 수량이 작업의 마지막 정정 기록과 다릅니다. 현재 실사 수량 조정을 사용하세요.");
      }
    }

    List<WorkEffectResults.Adjustment> auditRows =
        request.orchidGroupAdjustments().stream()
            .filter(adjustment -> changedAdjustmentIds.contains(adjustment.orchidGroupId()))
            .map(
                adjustment -> {
                  OrchidGroup group = groupsById.get(adjustment.orchidGroupId());
                  if (request.cancelResultCreation()) {
                    return new WorkEffectResults.Adjustment(
                        group.getId(),
                        group.getQuantity(),
                        group.getStatus(),
                        0,
                        OrchidGroupStatusPolicy.CREATION_CANCELED);
                  }
                  return new WorkEffectResults.Adjustment(
                      group.getId(),
                      group.getQuantity(),
                      group.getStatus(),
                      adjustment.quantity(),
                      adjustment.status().trim());
                })
            .toList();
    var references =
        structureChangeReferenceReader.getMutationReferences(
            originalOperationId, changedAdjustmentIds);
    return new WorkCorrectionPlan(auditRows, quantityChanges, references);
  }

  @Override
  public WorkMutationLink apply(
      Long correctionId, WorkCorrectionCommand request, WorkCorrectionPlan plan) {
    if (plan.adjustments().isEmpty()) return null;
    var changedIds =
        plan.adjustments().stream()
            .map(WorkEffectResults.Adjustment::orchidGroupId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    var references = plan.mutationReferences();
    RelatedOrchidGroupMutations related =
        references.legacySource()
            ? RelatedOrchidGroupMutations.legacy()
            : RelatedOrchidGroupMutations.current(references.mutationIds());
    var source = OrchidGroupMutationSources.workCorrection(correctionId);
    var mutation =
        request.cancelResultCreation()
            ? mutationEngine.cancelCreation(
                new CancelOrchidGroupCreationMutationCommand(
                    source,
                    changedIds.iterator().next(),
                    related,
                    request.workDate(),
                    request.reason()))
            : mutationEngine.correct(
                new CorrectOrchidGroupsMutationCommand(
                    source,
                    request.orchidGroupAdjustments().stream()
                        .filter(adjustment -> changedIds.contains(adjustment.orchidGroupId()))
                        .map(
                            adjustment ->
                                new CorrectOrchidGroupMutationItem(
                                    adjustment.orchidGroupId(),
                                    adjustment.quantity(),
                                    adjustment.status()))
                        .toList(),
                    related,
                    request.workDate(),
                    request.reason()));
    return new WorkMutationLink(mutation.mutationId(), mutation.correlationId());
  }

  private Set<Long> changedAdjustmentIds(
      WorkCorrectionCommand request, Set<Long> adjustmentIds, Map<Long, OrchidGroup> groupsById) {
    if (request.cancelResultCreation()) {
      OrchidGroup group = groupsById.get(adjustmentIds.iterator().next());
      if (OrchidGroupStatusPolicy.CREATION_CANCELED.equals(group.getStatus())) {
        throw new IllegalArgumentException("이미 생성 취소된 결과 난 묶음입니다.");
      }
      return adjustmentIds;
    }
    return request.orchidGroupAdjustments().stream()
        .filter(
            adjustment -> {
              OrchidGroup group = groupsById.get(adjustment.orchidGroupId());
              if (OrchidGroupStatusPolicy.CREATION_CANCELED.equals(group.getStatus())) {
                throw new IllegalArgumentException("생성 취소된 결과 난 묶음은 다시 보정할 수 없습니다.");
              }
              return !group.getQuantity().equals(adjustment.quantity())
                  || !group.getStatus().equals(adjustment.status().trim());
            })
        .map(OrchidGroupCorrectionInput::orchidGroupId)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
