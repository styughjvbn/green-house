package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupUsageInspector;
import com.greenhouse.backend.farm.application.orchid.mutation.CancelOrchidGroupCreationMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.CorrectOrchidGroupMutationItem;
import com.greenhouse.backend.farm.application.orchid.mutation.CorrectOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.application.orchid.mutation.RelatedOrchidGroupMutations;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroupStatusPolicy;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidStockCountRepository;
import com.greenhouse.backend.work.application.correction.OrchidGroupCorrectionInput;
import com.greenhouse.backend.work.application.correction.StructureChangeReferenceReader;
import com.greenhouse.backend.work.application.correction.WorkCorrectionCommand;
import com.greenhouse.backend.work.application.correction.WorkCorrectionPort;
import com.greenhouse.backend.work.application.correction.WorkCorrectionQuantityService;
import com.greenhouse.backend.work.application.correction.WorkOperationDateCorrectionService;
import com.greenhouse.backend.work.application.effect.WorkEffectResults;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.application.effect.WorkMutationLink;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FarmWorkCorrectionAdapter implements WorkCorrectionPort {

  private final StructureChangeReferenceReader structureChangeReferenceReader;

  private final WorkOperationDateCorrectionService workOperationDateCorrectionService;

  private final OrchidGroupRepository orchidGroupRepository;

  private final List<OrchidGroupUsageInspector> usageInspectors;

  private final OrchidGroupMutationEngine mutationEngine;

  private final WorkCorrectionQuantityService quantityService;

  private final OrchidStockCountRepository stockCounts;

  @Override
  public WorkExecutionResult correct(
      Long originalOperationId, Supplier<Long> correctionId, WorkCorrectionCommand request) {
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
    boolean workDateChanged =
        !workOperationDateCorrectionService
            .getWorkDate(originalOperationId)
            .equals(request.workDate());
    if (request.cancelResultCreation() && workDateChanged) {
      throw new IllegalArgumentException("결과 생성 취소와 작업일 보정은 별도로 처리해야 합니다.");
    }
    if (changedAdjustmentIds.isEmpty() && !workDateChanged && quantityChanges.isEmpty()) {
      throw new IllegalArgumentException("수량, 상태 또는 작업일 중 현재 값과 다른 보정 값이 필요합니다.");
    }
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
    Long eventId = correctionId.get();
    WorkMutationLink mutationLink = null;
    if (!changedAdjustmentIds.isEmpty()) {
      var references =
          structureChangeReferenceReader.getMutationReferences(
              originalOperationId, changedAdjustmentIds);
      RelatedOrchidGroupMutations related =
          references.legacySource()
              ? RelatedOrchidGroupMutations.legacy()
              : RelatedOrchidGroupMutations.current(references.mutationIds());
      var source = OrchidGroupMutationSources.workCorrection(eventId);
      var mutation =
          request.cancelResultCreation()
              ? mutationEngine.cancelCreation(
                  new CancelOrchidGroupCreationMutationCommand(
                      source,
                      changedAdjustmentIds.iterator().next(),
                      related,
                      request.workDate(),
                      request.reason()))
              : mutationEngine.correct(
                  new CorrectOrchidGroupsMutationCommand(
                      source,
                      request.orchidGroupAdjustments().stream()
                          .filter(
                              adjustment ->
                                  changedAdjustmentIds.contains(adjustment.orchidGroupId()))
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
      mutationLink = new WorkMutationLink(mutation.mutationId(), mutation.correlationId());
    }
    var dateCorrection =
        workOperationDateCorrectionService.correct(originalOperationId, request.workDate());
    var resultDetails =
        new WorkEffectResults.Corrected(
                originalOperationId, dateCorrection.before(), dateCorrection.after(), auditRows)
            .toMap();
    if (!quantityChanges.isEmpty()) resultDetails.put("quantityBalances", quantityChanges);
    return new WorkExecutionResult(
        "CORRECTION", resultDetails, List.copyOf(changedAdjustmentIds), mutationLink);
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
