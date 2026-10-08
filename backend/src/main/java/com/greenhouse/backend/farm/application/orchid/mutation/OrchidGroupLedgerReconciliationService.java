package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.PotSizeCode;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverage;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverageStatus;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryKind;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.repository.collection.OrchidGroupCollectionMemberRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.CorrectionMutationReconciliationRow;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupLedgerCoverageRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.ReconciliationEntryRow;
import com.greenhouse.backend.work.api.effect.WorkOrchidGroupLedgerRehearsalApi;
import com.greenhouse.backend.work.api.effect.WorkOrchidGroupLedgerRehearsalReport.CorrectionReference;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** ORCHID-CUTOVER: TARGET — 전환 전후 ledger 연속성과 업무 연결을 상시 검증한다. */
@Service
@RequiredArgsConstructor
public class OrchidGroupLedgerReconciliationService {

  private static final int BATCH_SIZE = 500;

  private static final BigDecimal MINIMUM_PLACEMENT_SPAN = BigDecimal.ONE;

  private final OrchidGroupLedgerCoverageRepository coverageRepository;

  private final OrchidGroupRepository orchidGroupRepository;

  private final OrchidGroupMutationRepository mutationRepository;

  private final OrchidGroupMutationEntryRepository entryRepository;

  private final OrchidGroupCollectionMemberRepository collectionMemberRepository;

  private final OrchidGroupMutationFingerprint fingerprint;

  private final WorkOrchidGroupLedgerRehearsalApi workInspector;

  private final List<OrchidGroupLedgerRehearsalInspector> externalInspectors;

  private final Clock clock;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public OrchidGroupLedgerReconciliationReport reconcile() {
    List<OrchidGroupLedgerReconciliationIssue> issues = new ArrayList<>();
    Optional<OrchidGroupLedgerCoverage> activeCoverage =
        coverageRepository.findFirstByStatusOrderByIdDesc(OrchidGroupLedgerCoverageStatus.ACTIVE);
    Optional<OrchidGroupLedgerCoverage> preparingCoverage =
        coverageRepository.findFirstByStatusOrderByIdDesc(
            OrchidGroupLedgerCoverageStatus.PREPARING);
    if (coverageRepository.countByStatus(OrchidGroupLedgerCoverageStatus.ACTIVE) > 1) {
      issues.add(
          issue(
              "MULTIPLE_ACTIVE_COVERAGES",
              "COVERAGE",
              "ACTIVE",
              "ACTIVE ledger coverage는 하나만 존재해야 합니다."));
    }
    if (coverageRepository.countByStatus(OrchidGroupLedgerCoverageStatus.PREPARING) > 1) {
      issues.add(
          issue(
              "MULTIPLE_PREPARING_COVERAGES",
              "COVERAGE",
              "PREPARING",
              "동시에 진행 중인 PREPARING coverage가 여러 개입니다."));
    }
    if (activeCoverage.isPresent() && preparingCoverage.isPresent()) {
      issues.add(
          issue(
              "ACTIVE_AND_PREPARING_COVERAGE",
              "COVERAGE",
              "GLOBAL",
              "ACTIVE coverage가 있는 동안 새 PREPARING coverage를 둘 수 없습니다."));
    }
    OrchidGroupLedgerCoverage coverage =
        activeCoverage.orElseGet(() -> preparingCoverage.orElse(null));
    if (coverage != null
        && coverage.getStatus() == OrchidGroupLedgerCoverageStatus.ACTIVE
        && coverage.getImportFingerprint() == null) {
      issues.add(
          issue(
              "MISSING_IMPORT_FINGERPRINT",
              "COVERAGE",
              coverage.getCutoverKey().toString(),
              "ACTIVE coverage에는 complete state-chain manifest fingerprint가 필요합니다."));
    }
    OrchidGroupLedgerReconciliationStage stage =
        coverage == null
            ? OrchidGroupLedgerReconciliationStage.PRE_BASELINE
            : coverage.getStatus() == OrchidGroupLedgerCoverageStatus.ACTIVE
                ? OrchidGroupLedgerReconciliationStage.ACTIVE
                : OrchidGroupLedgerReconciliationStage.BASELINE_PREPARING;

    List<OrchidGroupLedgerReconciliationGroup> groups = loadGroups();
    inspectCurrentState(groups, issues);
    inspectPlacement(groups, issues);
    inspectFarmReferences(groups, issues);
    inspectWorkReferences(groups, issues);
    externalInspectors.forEach(inspector -> issues.addAll(inspector.inspect(groups)));

    List<BaselineFingerprintEntry> baselineEntries;
    try (var entries = entryRepository.streamCurrentGroupChains()) {
      baselineEntries = inspectLedger(stage, coverage, groups, entries.iterator(), issues);
    }
    try (var entries = entryRepository.streamDeletedGroupChains()) {
      inspectDeletedLedger(stage, entries.iterator(), issues);
    }
    long mutationCount = mutationRepository.count();
    long entryCount = entryRepository.count();
    long emptyMutationCount = mutationRepository.countWithoutEntries();
    if (emptyMutationCount > 0) {
      issues.add(
          issue(
              "MUTATION_WITHOUT_ENTRY",
              "LEDGER",
              String.valueOf(emptyMutationCount),
              "Entry가 없는 Mutation이 존재합니다."));
    }

    String baselineFingerprint =
        coverage == null
            ? null
            : fingerprint.calculate(
                new BaselineFingerprintPayload(
                    coverage.getCutoverKey(),
                    coverage.getEffectiveBusinessDate(),
                    baselineEntries));
    if (coverage != null && coverage.getStatus() == OrchidGroupLedgerCoverageStatus.ACTIVE) {
      if (!Objects.equals(coverage.getBaselineGroupCount(), (long) baselineEntries.size())) {
        issues.add(
            issue(
                "BASELINE_GROUP_COUNT_MISMATCH",
                "COVERAGE",
                coverage.getCutoverKey().toString(),
                "저장된 baseline 그룹 수와 실제 BASELINE Entry 수가 다릅니다."));
      }
      if (!Objects.equals(coverage.getBaselineFingerprint(), baselineFingerprint)) {
        issues.add(
            issue(
                "BASELINE_FINGERPRINT_MISMATCH",
                "COVERAGE",
                coverage.getCutoverKey().toString(),
                "저장된 baseline fingerprint와 실제 BASELINE snapshot이 다릅니다."));
      }
    }

    long revisionedGroupCount =
        groups.stream().filter(group -> group.stateRevision() != null).count();
    String currentStateFingerprint =
        fingerprint.calculate(
            new CurrentStateFingerprintPayload(
                groups.stream()
                    .map(
                        group ->
                            new CurrentStateFingerprintEntry(
                                group.orchidGroupId(), group.stateRevision(), group.snapshot()))
                    .toList()));
    return new OrchidGroupLedgerReconciliationReport(
        Instant.now(clock),
        stage,
        coverage == null ? null : coverage.getCutoverKey(),
        coverage == null ? null : coverage.getStatus(),
        groups.size(),
        revisionedGroupCount,
        mutationCount,
        entryCount,
        baselineEntries.size(),
        baselineFingerprint,
        currentStateFingerprint,
        issues.isEmpty(),
        List.copyOf(issues));
  }

  private void inspectWorkReferences(
      List<OrchidGroupLedgerReconciliationGroup> groups,
      List<OrchidGroupLedgerReconciliationIssue> issues) {
    var existingGroupIds =
        new HashSet<>(
            groups.stream().map(OrchidGroupLedgerReconciliationGroup::orchidGroupId).toList());
    var workReport = workInspector.inspect();
    workReport.targetOrchidGroupIds().stream()
        .filter(groupId -> !existingGroupIds.contains(groupId))
        .forEach(
            groupId ->
                issues.add(
                    issue(
                        "DANGLING_WORK_TARGET_GROUP",
                        "WORK",
                        groupId.toString(),
                        "WorkOperationTarget이 존재하지 않는 난 묶음을 참조합니다.")));
    workReport.effectOrchidGroupIds().stream()
        .filter(groupId -> !existingGroupIds.contains(groupId))
        .forEach(
            groupId ->
                issues.add(
                    issue(
                        "DANGLING_WORK_EFFECT_GROUP",
                        "WORK",
                        groupId.toString(),
                        "WorkEffectOrchidGroup이 존재하지 않는 난 묶음을 참조합니다.")));

    inspectWorkCorrections(workReport.corrections(), existingGroupIds, issues);
    workReport
        .invalidExecutionIds()
        .forEach(
            executionId ->
                issues.add(
                    issue(
                        "INVALID_WORK_EXECUTION_PROGRESS",
                        "WORK",
                        executionId.toString(),
                        "processedQuantity, 계획 수량, 상태와 effectAppliedAt이 일치하지 않습니다.")));
    workReport
        .incompleteMutationLinkEffectIds()
        .forEach(
            effectId ->
                issues.add(
                    issue(
                        "INCOMPLETE_WORK_MUTATION_LINK",
                        "WORK",
                        effectId.toString(),
                        "WorkAppliedEffect의 mutationId와 correlationId가 함께 설정되지 않았습니다.")));
  }

  private void inspectWorkCorrections(
      List<CorrectionReference> references,
      Set<Long> existingGroupIds,
      List<OrchidGroupLedgerReconciliationIssue> issues) {
    for (int start = 0; start < references.size(); start += BATCH_SIZE) {
      var batch = references.subList(start, Math.min(start + BATCH_SIZE, references.size()));
      var ids =
          batch.stream()
              .map(CorrectionReference::mutationId)
              .filter(Objects::nonNull)
              .distinct()
              .toList();
      Map<Long, CorrectionMutationReconciliationRow> mutations =
          ids.isEmpty()
              ? Map.of()
              : mutationRepository.findCorrectionReconciliationRowsByIdIn(ids).stream()
                  .collect(Collectors.toMap(CorrectionMutationReconciliationRow::id, row -> row));
      for (var reference : batch) {
        reference.orchidGroupIds().stream()
            .filter(id -> !existingGroupIds.contains(id))
            .forEach(
                id ->
                    issues.add(
                        issue(
                            "DANGLING_WORK_CORRECTION_GROUP",
                            "WORK",
                            reference.id().toString(),
                            "보정 내역이 존재하지 않는 난 묶음을 참조합니다.")));
        var mutation =
            reference.mutationId() == null ? null : mutations.get(reference.mutationId());
        boolean valid =
            reference.changesGroups()
                ? mutation != null
                    && Objects.equals(mutation.correlationId(), reference.correlationId())
                    && mutation.sourceDomain() == OrchidGroupMutationSourceDomain.WORK
                    && Set.of(
                            OrchidGroupMutationType.CORRECTION,
                            OrchidGroupMutationType.CANCEL_CREATION)
                        .contains(mutation.mutationType())
                    && mutation.sourceType().equals("WORK_CORRECTION")
                    && mutation.sourceReferenceId().equals(reference.id().toString())
                : reference.mutationId() == null && reference.correlationId() == null;
        if (!valid)
          issues.add(
              issue(
                  "INVALID_WORK_CORRECTION_MUTATION_LINK",
                  "WORK",
                  reference.id().toString(),
                  "보정 이벤트와 Mutation 출처가 일치하지 않습니다."));
      }
    }
  }

  private void inspectFarmReferences(
      List<OrchidGroupLedgerReconciliationGroup> groups,
      List<OrchidGroupLedgerReconciliationIssue> issues) {
    var existingGroupIds =
        new HashSet<>(
            groups.stream().map(OrchidGroupLedgerReconciliationGroup::orchidGroupId).toList());
    collectionMemberRepository.findDistinctOrchidGroupIds().stream()
        .filter(groupId -> !existingGroupIds.contains(groupId))
        .forEach(
            groupId ->
                issues.add(
                    issue(
                        "DANGLING_COLLECTION_GROUP",
                        "FARM",
                        groupId.toString(),
                        "난 묶음 Collection membership이 존재하지 않는 난 묶음을 참조합니다.")));
  }

  private List<OrchidGroupLedgerReconciliationGroup> loadGroups() {
    var result = new ArrayList<OrchidGroupLedgerReconciliationGroup>();
    long afterId = 0;
    while (true) {
      var rows =
          orchidGroupRepository.findReconciliationGroupsAfter(
              afterId, PageRequest.of(0, BATCH_SIZE));
      if (rows.isEmpty()) return List.copyOf(result);
      for (var row : rows) {
        result.add(
            new OrchidGroupLedgerReconciliationGroup(
                row.id(), row.stateRevision(), row.snapshot(), row.maximumPosition()));
      }
      afterId = rows.getLast().id();
      if (rows.size() < BATCH_SIZE) return List.copyOf(result);
    }
  }

  private void inspectCurrentState(
      List<OrchidGroupLedgerReconciliationGroup> groups,
      List<OrchidGroupLedgerReconciliationIssue> issues) {
    for (OrchidGroupLedgerReconciliationGroup group : groups) {
      OrchidGroupStateSnapshot state = group.snapshot();
      if (state.quantity() == null
          || state.quantity() < 0
          || state.reservedQuantity() == null
          || state.reservedQuantity() < 0
          || state.quantity() != null
              && state.reservedQuantity() != null
              && state.reservedQuantity() > state.quantity()) {
        issues.add(
            groupIssue("INVALID_QUANTITY_INVARIANT", group, "수량은 0 이상이고 예약 수량은 전체 수량 이하여야 합니다."));
      }
      if (state.status() == null || state.status().isBlank()) {
        issues.add(groupIssue("MISSING_STATUS", group, "난 묶음 상태가 비어 있습니다."));
      }
      if (state.potSizeCode() == null || PotSizeCode.UNMAPPED.name().equals(state.potSizeCode())) {
        issues.add(groupIssue("UNMAPPED_POT_SIZE", group, "화분 크기를 canonical code로 변환해야 합니다."));
      }
      if (state.quantity() != null && state.quantity() > 0 && state.varietyId() == null) {
        issues.add(groupIssue("ACTIVE_GROUP_WITHOUT_VARIETY", group, "활성 난 묶음에는 품종 연결이 필요합니다."));
      }
      if (state.bedZoneId() == null) {
        issues.add(groupIssue("MISSING_BED_ZONE", group, "난 묶음에는 논리 구역 연결이 필요합니다."));
      }
    }
  }

  private void inspectPlacement(
      List<OrchidGroupLedgerReconciliationGroup> groups,
      List<OrchidGroupLedgerReconciliationIssue> issues) {
    Map<Long, List<OrchidGroupLedgerReconciliationGroup>> activeByZone =
        groups.stream()
            .filter(group -> group.snapshot().quantity() != null && group.snapshot().quantity() > 0)
            .filter(group -> group.snapshot().bedZoneId() != null)
            .collect(Collectors.groupingBy(group -> group.snapshot().bedZoneId()));
    for (List<OrchidGroupLedgerReconciliationGroup> zoneGroups : activeByZone.values()) {
      Map<Integer, List<OrchidGroupLedgerReconciliationGroup>> bySortOrder =
          zoneGroups.stream()
              .filter(group -> group.snapshot().sortOrder() != null)
              .collect(Collectors.groupingBy(group -> group.snapshot().sortOrder()));
      bySortOrder.values().stream()
          .filter(sameOrder -> sameOrder.size() > 1)
          .forEach(
              sameOrder ->
                  issues.add(
                      issue(
                          "DUPLICATE_ZONE_SORT_ORDER",
                          "FARM",
                          sameOrder.stream()
                              .map(group -> group.orchidGroupId().toString())
                              .collect(Collectors.joining(",")),
                          "같은 구역의 활성 난 묶음 sortOrder가 중복됩니다.")));
      List<OrchidGroupLedgerReconciliationGroup> validRanges = new ArrayList<>();
      for (OrchidGroupLedgerReconciliationGroup group : zoneGroups) {
        BigDecimal start = group.snapshot().startPosition();
        BigDecimal end = group.snapshot().endPosition();
        boolean valid =
            start != null
                && end != null
                && start.compareTo(BigDecimal.ZERO) >= 0
                && end.compareTo(start) > 0
                && end.subtract(start).compareTo(MINIMUM_PLACEMENT_SPAN) >= 0
                && (group.maximumPosition() == null || end.compareTo(group.maximumPosition()) <= 0);
        if (!valid) {
          issues.add(
              groupIssue("INVALID_PLACEMENT_RANGE", group, "활성 난 묶음의 배치 범위가 구역 또는 배드 범위를 벗어납니다."));
        } else {
          validRanges.add(group);
        }
      }
      validRanges.sort(Comparator.comparing(group -> group.snapshot().startPosition()));
      for (int index = 1; index < validRanges.size(); index++) {
        var previous = validRanges.get(index - 1);
        var current = validRanges.get(index);
        if (current.snapshot().startPosition().compareTo(previous.snapshot().endPosition()) < 0) {
          issues.add(
              issue(
                  "OVERLAPPING_PLACEMENT",
                  "FARM",
                  previous.orchidGroupId() + "," + current.orchidGroupId(),
                  "같은 구역의 활성 난 묶음 배치 범위가 겹칩니다."));
        }
      }
    }
  }

  private List<BaselineFingerprintEntry> inspectLedger(
      OrchidGroupLedgerReconciliationStage stage,
      OrchidGroupLedgerCoverage coverage,
      List<OrchidGroupLedgerReconciliationGroup> groups,
      Iterator<ReconciliationEntryRow> entries,
      List<OrchidGroupLedgerReconciliationIssue> issues) {
    var baselines = new ArrayList<BaselineFingerprintEntry>();
    var next = entries.hasNext() ? entries.next() : null;
    for (var group : groups) {
      ReconciliationEntryRow first = null;
      ReconciliationEntryRow previous = null;
      while (next != null && Objects.equals(next.orchidGroupId(), group.orchidGroupId())) {
        var entry = next;
        if (first == null) first = entry;
        if (isCoverageBaseline(entry, coverage)) {
          baselines.add(
              new BaselineFingerprintEntry(
                  entry.orchidGroupId(), canonicalSnapshot(entry.afterState())));
        }
        if (stage != OrchidGroupLedgerReconciliationStage.PRE_BASELINE
            && group.stateRevision() != null
            && previous != null) {
          if (!Objects.equals(previous.stateRevisionAfter(), entry.stateRevisionBefore())) {
            issues.add(groupIssue("REVISION_GAP", group, "MutationEntry revision이 연속되지 않습니다."));
          }
          if (!sameSnapshot(previous.afterState(), entry.beforeState())) {
            issues.add(
                groupIssue(
                    "SNAPSHOT_CHAIN_MISMATCH",
                    group,
                    "이전 after snapshot과 다음 before snapshot이 다릅니다."));
          }
        }
        previous = entry;
        next = entries.hasNext() ? entries.next() : null;
      }
      if (stage == OrchidGroupLedgerReconciliationStage.PRE_BASELINE) {
        if (group.stateRevision() != null || first != null)
          issues.add(
              groupIssue(
                  "LEDGER_STATE_BEFORE_COVERAGE",
                  group,
                  "Coverage가 없는데 revision 또는 MutationEntry가 존재합니다."));
        continue;
      }
      if (group.stateRevision() == null || first == null) {
        issues.add(
            groupIssue("MISSING_LEDGER_CHAIN", group, "Coverage 대상 난 묶음에 revision chain이 없습니다."));
        continue;
      }
      if (first.entryKind() != OrchidGroupMutationEntryKind.CREATE
          && !isCoverageBaseline(first, coverage)) {
        if (stage == OrchidGroupLedgerReconciliationStage.BASELINE_PREPARING)
          issues.add(
              groupIssue(
                  "MISSING_CUTOVER_BASELINE",
                  group,
                  "PREPARING revision chain은 cutover baseline 또는 전환 후 CREATE로 시작해야 합니다."));
        else
          issues.add(
              groupIssue(
                  "INVALID_LEDGER_ORIGIN",
                  group,
                  "ACTIVE revision chain은 cutover baseline 또는 전환 후 CREATE로 시작해야 합니다."));
      }
      if (!Objects.equals(group.stateRevision(), previous.stateRevisionAfter()))
        issues.add(
            groupIssue(
                "CURRENT_REVISION_MISMATCH",
                group,
                "현재 stateRevision과 마지막 MutationEntry revision이 다릅니다."));
      if (!sameSnapshot(group.snapshot(), previous.afterState()))
        issues.add(
            groupIssue(
                "CURRENT_SNAPSHOT_MISMATCH",
                group,
                "현재 난 묶음 상태와 마지막 MutationEntry snapshot이 다릅니다."));
    }
    return baselines;
  }

  private void inspectDeletedLedger(
      OrchidGroupLedgerReconciliationStage stage,
      Iterator<ReconciliationEntryRow> entries,
      List<OrchidGroupLedgerReconciliationIssue> issues) {
    var next = entries.hasNext() ? entries.next() : null;
    while (next != null) {
      var first = next;
      var groupId = first.orchidGroupId();
      ReconciliationEntryRow previous = null;
      while (next != null && Objects.equals(next.orchidGroupId(), groupId)) {
        var entry = next;
        if (stage != OrchidGroupLedgerReconciliationStage.PRE_BASELINE && previous != null) {
          if (!Objects.equals(previous.stateRevisionAfter(), entry.stateRevisionBefore()))
            issues.add(
                issue(
                    "REVISION_GAP",
                    "FARM",
                    groupId.toString(),
                    "삭제된 난 묶음 MutationEntry revision이 연속되지 않습니다."));
          if (!sameSnapshot(previous.afterState(), entry.beforeState()))
            issues.add(
                issue(
                    "SNAPSHOT_CHAIN_MISMATCH",
                    "FARM",
                    groupId.toString(),
                    "삭제된 난 묶음의 snapshot chain이 연속되지 않습니다."));
        }
        previous = entry;
        next = entries.hasNext() ? entries.next() : null;
      }
      if (stage == OrchidGroupLedgerReconciliationStage.PRE_BASELINE) {
        issues.add(
            issue(
                "LEDGER_STATE_BEFORE_COVERAGE",
                "FARM",
                groupId.toString(),
                "Coverage가 없는데 삭제된 난 묶음 revision chain이 존재합니다."));
        continue;
      }
      if (first.entryKind() != OrchidGroupMutationEntryKind.CREATE
          && first.entryKind() != OrchidGroupMutationEntryKind.BASELINE)
        issues.add(
            issue(
                "INVALID_LEDGER_ORIGIN",
                "FARM",
                groupId.toString(),
                "삭제된 난 묶음 chain은 BASELINE 또는 CREATE로 시작해야 합니다."));
      if (previous.entryKind() != OrchidGroupMutationEntryKind.DELETE
          || previous.afterState() != null)
        issues.add(
            issue(
                "MISSING_DELETE_TOMBSTONE",
                "FARM",
                groupId.toString(),
                "현재 행이 없는 난 묶음 chain은 DELETE로 종료되어야 합니다."));
    }
  }

  private boolean isCoverageBaseline(
      ReconciliationEntryRow entry, OrchidGroupLedgerCoverage coverage) {
    return coverage != null
        && entry.entryKind() == OrchidGroupMutationEntryKind.BASELINE
        && entry.mutationType() == OrchidGroupMutationType.BASELINE_IMPORT
        && entry.sourceDomain() == OrchidGroupMutationSourceDomain.MIGRATION
        && coverage.getCutoverKey().toString().equals(entry.sourceReferenceId());
  }

  private boolean sameSnapshot(OrchidGroupStateSnapshot first, OrchidGroupStateSnapshot second) {
    return Objects.equals(canonicalSnapshot(first), canonicalSnapshot(second));
  }

  private OrchidGroupStateSnapshot canonicalSnapshot(OrchidGroupStateSnapshot snapshot) {
    if (snapshot == null) {
      return null;
    }
    return snapshot.canonical();
  }

  private OrchidGroupLedgerReconciliationIssue groupIssue(
      String code, OrchidGroupLedgerReconciliationGroup group, String message) {
    return OrchidGroupLedgerReconciliationIssue.group(code, group.orchidGroupId(), message);
  }

  private OrchidGroupLedgerReconciliationIssue issue(
      String code, String domain, String referenceId, String message) {
    return new OrchidGroupLedgerReconciliationIssue(code, domain, referenceId, message);
  }

  private record BaselineFingerprintPayload(
      UUID cutoverKey, LocalDate effectiveBusinessDate, List<BaselineFingerprintEntry> groups) {}

  private record BaselineFingerprintEntry(Long orchidGroupId, OrchidGroupStateSnapshot snapshot) {}

  private record CurrentStateFingerprintPayload(List<CurrentStateFingerprintEntry> groups) {}

  private record CurrentStateFingerprintEntry(
      Long orchidGroupId, Long stateRevision, OrchidGroupStateSnapshot snapshot) {}
}
