package com.greenhouse.backend.farm.inbound.integration;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.api.orchid.CompensateCreateMutationsCommand;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationType;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEffectiveHeadPolicy;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import com.greenhouse.backend.farm.domain.inbound.InboundStatus;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelationType;
import com.greenhouse.backend.farm.orchid.domain.OrchidGroup;
import com.greenhouse.backend.farm.orchid.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.inbound.InboundRecordRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRelationRepository;
import com.greenhouse.backend.farm.spi.orchid.OrchidGroupUsageInspector;
import com.greenhouse.backend.work.spi.operation.PottingVoidPort;
import com.greenhouse.backend.work.spi.operation.StructureChangeVoidPort.Blocker;
import com.greenhouse.backend.work.spi.operation.StructureChangeVoidPort.OrchidGroupSummary;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
@RequiredArgsConstructor
public class FarmPottingVoidAdapter implements PottingVoidPort {

  private final InboundRecordRepository inboundRecordRepository;

  private final OrchidGroupRepository orchidGroupRepository;

  private final OrchidGroupMutationEntryRepository entryRepository;

  private final OrchidGroupMutationRelationRepository relationRepository;

  private final List<OrchidGroupUsageInspector> usageInspectors;

  private final OrchidGroupMutationEffectiveHeadPolicy effectiveHeadPolicy;

  private final OrchidGroupMutationEngine mutationEngine;

  @Override
  @Transactional(readOnly = true)
  public Inspection inspect(Long workOperationId, List<Effect> effects) {
    return inspectCurrent(workOperationId, effects, null);
  }

  @Override
  public Inspection inspectForUpdate(Long workOperationId, List<Effect> effects) {
    var ids =
        effects.stream()
            .map(Effect::inboundRecordId)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();
    return inspectCurrent(
        workOperationId, effects, inboundRecordRepository.findAllForUpdateByIdIn(ids));
  }

  @Override
  public Long compensate(
      Long workOperationId,
      String requestKey,
      List<Effect> effects,
      LocalDate businessDate,
      String reason,
      boolean reopenInboundRecords) {
    List<Long> inboundIds =
        effects.stream()
            .map(Effect::inboundRecordId)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();
    List<InboundRecord> records = inboundRecordRepository.findAllForUpdateByIdIn(inboundIds);
    if (records.size() != inboundIds.size()) {
      throw new NotFoundException("다시 대기 상태로 전환할 입고 기록을 모두 찾을 수 없습니다.");
    }
    Inspection inspection = inspectCurrent(workOperationId, effects, records);
    if (!inspection.blockers().isEmpty()) {
      throw new IllegalArgumentException(inspection.blockers().getFirst().message());
    }
    List<Long> mutationIds = effects.stream().map(Effect::mutationId).distinct().sorted().toList();
    var compensation =
        mutationEngine.compensateCreations(
            new CompensateCreateMutationsCommand(
                OrchidGroupMutationSources.work(workOperationId, "VOID:" + requestKey),
                mutationIds,
                businessDate,
                reason));
    if (reopenInboundRecords) {
      records.forEach(InboundRecord::reopenAfterPottingVoid);
    }
    return compensation.mutationId();
  }

  private Inspection inspectCurrent(
      Long workOperationId, List<Effect> effects, List<InboundRecord> lockedRecords) {
    var blockers = new ArrayList<Blocker>();
    if (effects.isEmpty()
        || effects.stream()
            .anyMatch(effect -> effect.inboundRecordId() == null || effect.mutationId() == null)) {
      return new Inspection(
          List.of(),
          List.of(new Blocker("POTTING_EFFECT_MISSING", "포트 작업과 입고 기록의 연결을 확인할 수 없습니다.", 1)));
    }
    List<Long> mutationIds = effects.stream().map(Effect::mutationId).distinct().sorted().toList();
    List<OrchidGroupMutationEntry> entries =
        entryRepository.findByMutationIdInOrderByMutationIdAscIdAsc(mutationIds);
    if (entries.isEmpty()
        || entries.stream().map(entry -> entry.getMutation().getId()).distinct().count()
            != mutationIds.size()
        || entries.stream()
            .anyMatch(
                entry ->
                    entry.getMutation().getMutationType() != OrchidGroupMutationType.CREATE
                        || entry.getBeforeState() != null)) {
      blockers.add(new Blocker("MUTATION_NOT_REVERSIBLE", "포트 작업으로 생성된 난 묶음을 확인할 수 없습니다.", 1));
    }
    if (relationRepository.existsByRelatedMutationIdInAndRelationType(
        mutationIds, OrchidGroupMutationRelationType.COMPENSATES)) {
      blockers.add(new Blocker("ALREADY_COMPENSATED", "이미 취소된 작업 효과가 포함되어 있습니다.", 1));
    }
    Set<Long> resultIds =
        entries.stream()
            .map(OrchidGroupMutationEntry::getOrchidGroupId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Map<Long, OrchidGroup> groups =
        (lockedRecords == null
                ? orchidGroupRepository.findAllById(resultIds)
                : orchidGroupRepository.findAllForUpdateByIdIn(resultIds))
            .stream().collect(Collectors.toMap(OrchidGroup::getId, Function.identity()));
    long changed = effectiveHeadPolicy.countGroupsNotAtEffectiveHead(entries, groups);
    if (changed > 0) {
      blockers.add(new Blocker("DOWNSTREAM_MUTATION", "상쇄되지 않은 후속 변경이 있는 난 묶음이 있습니다.", changed));
    }
    Map<Long, Long> inboundIdByMutationId =
        effects.stream()
            .collect(
                Collectors.toMap(
                    Effect::mutationId, Effect::inboundRecordId, (left, right) -> left));
    long mismatchedResults =
        entries.stream()
            .filter(
                entry -> {
                  OrchidGroup group = groups.get(entry.getOrchidGroupId());
                  Long expectedInboundId = inboundIdByMutationId.get(entry.getMutation().getId());
                  return group == null
                      || group.getInboundRecord() == null
                      || !group.getInboundRecord().getId().equals(expectedInboundId);
                })
            .count();
    if (mismatchedResults > 0) {
      blockers.add(
          new Blocker(
              "POTTING_RESULT_MISMATCH", "포트 작업 결과와 입고 기록 연결이 일치하지 않습니다.", mismatchedResults));
    }
    Set<Long> inboundIds =
        effects.stream().map(Effect::inboundRecordId).collect(Collectors.toSet());
    usageInspectors.stream()
        .flatMap(inspector -> inspector.inspect(resultIds, workOperationId, inboundIds).stream())
        .forEach(usage -> blockers.add(new Blocker(usage.code(), usage.message(), usage.count())));
    List<InboundRecord> records =
        lockedRecords == null ? inboundRecordRepository.findByIdIn(inboundIds) : lockedRecords;
    long invalidInboundCount =
        records.stream().filter(record -> record.getStatus() != InboundStatus.PLACED).count()
            + Math.max(0, inboundIds.size() - records.size());
    if (invalidInboundCount > 0) {
      blockers.add(
          new Blocker("INBOUND_CHANGED", "배치 완료 상태가 아닌 입고 기록이 있습니다.", invalidInboundCount));
    }
    return new Inspection(
        resultIds.stream()
            .sorted()
            .map(
                id -> {
                  OrchidGroup group = groups.get(id);
                  return group == null
                      ? new OrchidGroupSummary(id, null, null)
                      : new OrchidGroupSummary(id, group.getVarietyName(), group.getQuantity());
                })
            .toList(),
        blockers);
  }
}
