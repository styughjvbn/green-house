package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.PotSizeCode;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverage;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverageStatus;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryKind;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.repository.collection.OrchidGroupCollectionMemberRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupLedgerCoverageRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRepository;
import com.greenhouse.backend.work.application.effect.WorkOrchidGroupLedgerRehearsalInspector;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ORCHID-CUTOVER: TARGET — 전환 전후 ledger 연속성과 업무 연결을 상시 검증한다.
 */
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

	private final WorkOrchidGroupLedgerRehearsalInspector workInspector;

	private final List<OrchidGroupLedgerRehearsalInspector> externalInspectors;

	private final Clock clock;

	@Transactional(readOnly = true)
	public OrchidGroupLedgerReconciliationReport reconcile() {
		List<OrchidGroupLedgerReconciliationIssue> issues = new ArrayList<>();
		Optional<OrchidGroupLedgerCoverage> activeCoverage = coverageRepository
			.findFirstByStatusOrderByIdDesc(OrchidGroupLedgerCoverageStatus.ACTIVE);
		Optional<OrchidGroupLedgerCoverage> preparingCoverage = coverageRepository
			.findFirstByStatusOrderByIdDesc(OrchidGroupLedgerCoverageStatus.PREPARING);
		if (coverageRepository.countByStatus(OrchidGroupLedgerCoverageStatus.ACTIVE) > 1) {
			issues
				.add(issue("MULTIPLE_ACTIVE_COVERAGES", "COVERAGE", "ACTIVE", "ACTIVE ledger coverage는 하나만 존재해야 합니다."));
		}
		if (coverageRepository.countByStatus(OrchidGroupLedgerCoverageStatus.PREPARING) > 1) {
			issues.add(issue("MULTIPLE_PREPARING_COVERAGES", "COVERAGE", "PREPARING",
					"동시에 진행 중인 PREPARING coverage가 여러 개입니다."));
		}
		if (activeCoverage.isPresent() && preparingCoverage.isPresent()) {
			issues.add(issue("ACTIVE_AND_PREPARING_COVERAGE", "COVERAGE", "GLOBAL",
					"ACTIVE coverage가 있는 동안 새 PREPARING coverage를 둘 수 없습니다."));
		}
		OrchidGroupLedgerCoverage coverage = activeCoverage.orElseGet(() -> preparingCoverage.orElse(null));
		if (coverage != null && coverage.getStatus() == OrchidGroupLedgerCoverageStatus.ACTIVE
				&& coverage.getImportFingerprint() == null) {
			issues.add(issue("MISSING_IMPORT_FINGERPRINT", "COVERAGE", coverage.getCutoverKey().toString(),
					"ACTIVE coverage에는 complete state-chain manifest fingerprint가 필요합니다."));
		}
		OrchidGroupLedgerReconciliationStage stage = coverage == null
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

		Map<Long, List<OrchidGroupMutationEntry>> entriesByGroupId = loadEntries(groups);
		inspectLedger(stage, coverage, groups, entriesByGroupId, issues);
		inspectDeletedLedger(stage, entryRepository.findChainsWithoutCurrentGroup(), issues);
		long mutationCount = mutationRepository.count();
		long entryCount = entryRepository.count();
		long emptyMutationCount = mutationRepository.countWithoutEntries();
		if (emptyMutationCount > 0) {
			issues.add(issue("MUTATION_WITHOUT_ENTRY", "LEDGER", String.valueOf(emptyMutationCount),
					"Entry가 없는 Mutation이 존재합니다."));
		}

		List<BaselineFingerprintEntry> baselineEntries = baselineEntries(coverage, entriesByGroupId);
		String baselineFingerprint = coverage == null ? null
				: fingerprint.calculate(new BaselineFingerprintPayload(coverage.getCutoverKey(),
						coverage.getEffectiveBusinessDate(), baselineEntries));
		if (coverage != null && coverage.getStatus() == OrchidGroupLedgerCoverageStatus.ACTIVE) {
			if (!Objects.equals(coverage.getBaselineGroupCount(), (long) baselineEntries.size())) {
				issues.add(issue("BASELINE_GROUP_COUNT_MISMATCH", "COVERAGE", coverage.getCutoverKey().toString(),
						"저장된 baseline 그룹 수와 실제 BASELINE Entry 수가 다릅니다."));
			}
			if (!Objects.equals(coverage.getBaselineFingerprint(), baselineFingerprint)) {
				issues.add(issue("BASELINE_FINGERPRINT_MISMATCH", "COVERAGE", coverage.getCutoverKey().toString(),
						"저장된 baseline fingerprint와 실제 BASELINE snapshot이 다릅니다."));
			}
		}

		long revisionedGroupCount = groups.stream().filter(group -> group.stateRevision() != null).count();
		String currentStateFingerprint = fingerprint.calculate(new CurrentStateFingerprintPayload(groups.stream()
			.map(group -> new CurrentStateFingerprintEntry(group.orchidGroupId(), group.stateRevision(),
					group.snapshot()))
			.toList()));
		return new OrchidGroupLedgerReconciliationReport(Instant.now(clock), stage,
				coverage == null ? null : coverage.getCutoverKey(), coverage == null ? null : coverage.getStatus(),
				groups.size(), revisionedGroupCount, mutationCount, entryCount, baselineEntries.size(),
				baselineFingerprint, currentStateFingerprint, issues.isEmpty(), List.copyOf(issues));
	}

	private void inspectWorkReferences(List<OrchidGroupLedgerReconciliationGroup> groups,
			List<OrchidGroupLedgerReconciliationIssue> issues) {
		var existingGroupIds = new HashSet<>(
				groups.stream().map(OrchidGroupLedgerReconciliationGroup::orchidGroupId).toList());
		var workReport = workInspector.inspect();
		workReport.targetOrchidGroupIds()
			.stream()
			.filter(groupId -> !existingGroupIds.contains(groupId))
			.forEach(groupId -> issues.add(issue("DANGLING_WORK_TARGET_GROUP", "WORK", groupId.toString(),
					"WorkOperationTarget이 존재하지 않는 난 묶음을 참조합니다.")));
		workReport.effectOrchidGroupIds()
			.stream()
			.filter(groupId -> !existingGroupIds.contains(groupId))
			.forEach(groupId -> issues.add(issue("DANGLING_WORK_EFFECT_GROUP", "WORK", groupId.toString(),
					"WorkEffectOrchidGroup이 존재하지 않는 난 묶음을 참조합니다.")));
		workReport.invalidExecutionIds()
			.forEach(executionId -> issues.add(issue("INVALID_WORK_EXECUTION_PROGRESS", "WORK", executionId.toString(),
					"processedQuantity, 계획 수량, 상태와 effectAppliedAt이 일치하지 않습니다.")));
		workReport.incompleteMutationLinkEffectIds()
			.forEach(effectId -> issues.add(issue("INCOMPLETE_WORK_MUTATION_LINK", "WORK", effectId.toString(),
					"WorkAppliedEffect의 mutationId와 correlationId가 함께 설정되지 않았습니다.")));
	}

	private void inspectFarmReferences(List<OrchidGroupLedgerReconciliationGroup> groups,
			List<OrchidGroupLedgerReconciliationIssue> issues) {
		var existingGroupIds = new HashSet<>(
				groups.stream().map(OrchidGroupLedgerReconciliationGroup::orchidGroupId).toList());
		collectionMemberRepository.findDistinctOrchidGroupIds()
			.stream()
			.filter(groupId -> !existingGroupIds.contains(groupId))
			.forEach(groupId -> issues.add(issue("DANGLING_COLLECTION_GROUP", "FARM", groupId.toString(),
					"난 묶음 Collection membership이 존재하지 않는 난 묶음을 참조합니다.")));
	}

	private List<OrchidGroupLedgerReconciliationGroup> loadGroups() {
		List<OrchidGroupLedgerReconciliationGroup> result = new ArrayList<>();
		long afterId = 0L;
		while (true) {
			List<Long> ids = orchidGroupRepository.findIdsAfter(afterId, PageRequest.of(0, BATCH_SIZE));
			if (ids.isEmpty()) {
				return List.copyOf(result);
			}
			Map<Long, OrchidGroup> groupsById = orchidGroupRepository.findDetailsByIds(ids)
				.stream()
				.collect(Collectors.toMap(OrchidGroup::getId, Function.identity()));
			ids.stream()
				.map(groupsById::get)
				.filter(Objects::nonNull)
				.forEach(group -> result.add(new OrchidGroupLedgerReconciliationGroup(group.getId(),
						group.getStateRevision(), canonicalSnapshot(OrchidGroupStateSnapshot.from(group)),
						group.getBedZone() == null || group.getBedZone().getPhysicalBed() == null ? null
								: group.getBedZone().getPhysicalBed().getPositionUnitCount())));
			afterId = ids.getLast();
		}
	}

	private Map<Long, List<OrchidGroupMutationEntry>> loadEntries(List<OrchidGroupLedgerReconciliationGroup> groups) {
		Map<Long, List<OrchidGroupMutationEntry>> result = new LinkedHashMap<>();
		for (int offset = 0; offset < groups.size(); offset += BATCH_SIZE) {
			List<Long> ids = groups.subList(offset, Math.min(offset + BATCH_SIZE, groups.size()))
				.stream()
				.map(OrchidGroupLedgerReconciliationGroup::orchidGroupId)
				.toList();
			entryRepository.findStateChainByOrchidGroupIdIn(ids)
				.forEach(entry -> result.computeIfAbsent(entry.getOrchidGroupId(), ignored -> new ArrayList<>())
					.add(entry));
		}
		return result;
	}

	private void inspectCurrentState(List<OrchidGroupLedgerReconciliationGroup> groups,
			List<OrchidGroupLedgerReconciliationIssue> issues) {
		for (OrchidGroupLedgerReconciliationGroup group : groups) {
			OrchidGroupStateSnapshot state = group.snapshot();
			if (state.quantity() == null || state.quantity() < 0 || state.reservedQuantity() == null
					|| state.reservedQuantity() < 0 || state.quantity() != null && state.reservedQuantity() != null
							&& state.reservedQuantity() > state.quantity()) {
				issues.add(groupIssue("INVALID_QUANTITY_INVARIANT", group, "수량은 0 이상이고 예약 수량은 전체 수량 이하여야 합니다."));
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

	private void inspectPlacement(List<OrchidGroupLedgerReconciliationGroup> groups,
			List<OrchidGroupLedgerReconciliationIssue> issues) {
		Map<Long, List<OrchidGroupLedgerReconciliationGroup>> activeByZone = groups.stream()
			.filter(group -> group.snapshot().quantity() != null && group.snapshot().quantity() > 0)
			.filter(group -> group.snapshot().bedZoneId() != null)
			.collect(Collectors.groupingBy(group -> group.snapshot().bedZoneId()));
		for (List<OrchidGroupLedgerReconciliationGroup> zoneGroups : activeByZone.values()) {
			Map<Integer, List<OrchidGroupLedgerReconciliationGroup>> bySortOrder = zoneGroups.stream()
				.filter(group -> group.snapshot().sortOrder() != null)
				.collect(Collectors.groupingBy(group -> group.snapshot().sortOrder()));
			bySortOrder.values()
				.stream()
				.filter(sameOrder -> sameOrder.size() > 1)
				.forEach(sameOrder -> issues.add(issue("DUPLICATE_ZONE_SORT_ORDER", "FARM",
						sameOrder.stream()
							.map(group -> group.orchidGroupId().toString())
							.collect(Collectors.joining(",")),
						"같은 구역의 활성 난 묶음 sortOrder가 중복됩니다.")));
			List<OrchidGroupLedgerReconciliationGroup> validRanges = new ArrayList<>();
			for (OrchidGroupLedgerReconciliationGroup group : zoneGroups) {
				BigDecimal start = group.snapshot().startPosition();
				BigDecimal end = group.snapshot().endPosition();
				boolean valid = start != null && end != null && start.compareTo(BigDecimal.ZERO) >= 0
						&& end.compareTo(start) > 0 && end.subtract(start).compareTo(MINIMUM_PLACEMENT_SPAN) >= 0
						&& (group.maximumPosition() == null || end.compareTo(group.maximumPosition()) <= 0);
				if (!valid) {
					issues.add(groupIssue("INVALID_PLACEMENT_RANGE", group, "활성 난 묶음의 배치 범위가 구역 또는 배드 범위를 벗어납니다."));
				}
				else {
					validRanges.add(group);
				}
			}
			validRanges.sort(Comparator.comparing(group -> group.snapshot().startPosition()));
			for (int index = 1; index < validRanges.size(); index++) {
				var previous = validRanges.get(index - 1);
				var current = validRanges.get(index);
				if (current.snapshot().startPosition().compareTo(previous.snapshot().endPosition()) < 0) {
					issues.add(issue("OVERLAPPING_PLACEMENT", "FARM",
							previous.orchidGroupId() + "," + current.orchidGroupId(), "같은 구역의 활성 난 묶음 배치 범위가 겹칩니다."));
				}
			}
		}
	}

	private void inspectLedger(OrchidGroupLedgerReconciliationStage stage, OrchidGroupLedgerCoverage coverage,
			List<OrchidGroupLedgerReconciliationGroup> groups,
			Map<Long, List<OrchidGroupMutationEntry>> entriesByGroupId,
			List<OrchidGroupLedgerReconciliationIssue> issues) {
		for (OrchidGroupLedgerReconciliationGroup group : groups) {
			List<OrchidGroupMutationEntry> entries = entriesByGroupId.getOrDefault(group.orchidGroupId(), List.of());
			if (stage == OrchidGroupLedgerReconciliationStage.PRE_BASELINE) {
				if (group.stateRevision() != null || !entries.isEmpty()) {
					issues.add(groupIssue("LEDGER_STATE_BEFORE_COVERAGE", group,
							"Coverage가 없는데 revision 또는 MutationEntry가 존재합니다."));
				}
				continue;
			}
			if (group.stateRevision() == null || entries.isEmpty()) {
				issues.add(groupIssue("MISSING_LEDGER_CHAIN", group, "Coverage 대상 난 묶음에 revision chain이 없습니다."));
				continue;
			}
			OrchidGroupMutationEntry previous = null;
			for (OrchidGroupMutationEntry entry : entries) {
				if (previous != null) {
					if (!Objects.equals(previous.getStateRevisionAfter(), entry.getStateRevisionBefore())) {
						issues.add(groupIssue("REVISION_GAP", group, "MutationEntry revision이 연속되지 않습니다."));
					}
					if (!sameSnapshot(previous.getAfterState(), entry.getBeforeState())) {
						issues.add(groupIssue("SNAPSHOT_CHAIN_MISMATCH", group,
								"이전 after snapshot과 다음 before snapshot이 다릅니다."));
					}
				}
				previous = entry;
			}
			OrchidGroupMutationEntry first = entries.getFirst();
			if (stage == OrchidGroupLedgerReconciliationStage.BASELINE_PREPARING
					&& first.getEntryKind() != OrchidGroupMutationEntryKind.CREATE
					&& !isCoverageBaseline(first, coverage)) {
				issues.add(groupIssue("MISSING_CUTOVER_BASELINE", group,
						"PREPARING revision chain은 cutover baseline 또는 전환 후 CREATE로 시작해야 합니다."));
			}
			if (stage == OrchidGroupLedgerReconciliationStage.ACTIVE
					&& first.getEntryKind() != OrchidGroupMutationEntryKind.CREATE
					&& !isCoverageBaseline(first, coverage)) {
				issues.add(groupIssue("INVALID_LEDGER_ORIGIN", group,
						"ACTIVE revision chain은 cutover baseline 또는 전환 후 CREATE로 시작해야 합니다."));
			}
			if (!Objects.equals(group.stateRevision(), previous.getStateRevisionAfter())) {
				issues.add(groupIssue("CURRENT_REVISION_MISMATCH", group,
						"현재 stateRevision과 마지막 MutationEntry revision이 다릅니다."));
			}
			if (!sameSnapshot(group.snapshot(), previous.getAfterState())) {
				issues.add(groupIssue("CURRENT_SNAPSHOT_MISMATCH", group,
						"현재 난 묶음 상태와 마지막 MutationEntry snapshot이 다릅니다."));
			}
		}
	}

	private void inspectDeletedLedger(OrchidGroupLedgerReconciliationStage stage,
			List<OrchidGroupMutationEntry> orphanEntries, List<OrchidGroupLedgerReconciliationIssue> issues) {
		Map<Long, List<OrchidGroupMutationEntry>> entriesByGroup = orphanEntries.stream()
			.collect(Collectors.groupingBy(OrchidGroupMutationEntry::getOrchidGroupId, LinkedHashMap::new,
					Collectors.toList()));
		for (var groupEntries : entriesByGroup.entrySet()) {
			Long groupId = groupEntries.getKey();
			List<OrchidGroupMutationEntry> entries = groupEntries.getValue();
			if (stage == OrchidGroupLedgerReconciliationStage.PRE_BASELINE) {
				issues.add(issue("LEDGER_STATE_BEFORE_COVERAGE", "FARM", groupId.toString(),
						"Coverage가 없는데 삭제된 난 묶음 revision chain이 존재합니다."));
				continue;
			}
			OrchidGroupMutationEntry previous = null;
			for (OrchidGroupMutationEntry entry : entries) {
				if (previous != null) {
					if (!Objects.equals(previous.getStateRevisionAfter(), entry.getStateRevisionBefore())) {
						issues.add(issue("REVISION_GAP", "FARM", groupId.toString(),
								"삭제된 난 묶음 MutationEntry revision이 연속되지 않습니다."));
					}
					if (!sameSnapshot(previous.getAfterState(), entry.getBeforeState())) {
						issues.add(issue("SNAPSHOT_CHAIN_MISMATCH", "FARM", groupId.toString(),
								"삭제된 난 묶음의 snapshot chain이 연속되지 않습니다."));
					}
				}
				previous = entry;
			}
			OrchidGroupMutationEntry first = entries.getFirst();
			if (first.getEntryKind() != OrchidGroupMutationEntryKind.CREATE
					&& first.getEntryKind() != OrchidGroupMutationEntryKind.BASELINE) {
				issues.add(issue("INVALID_LEDGER_ORIGIN", "FARM", groupId.toString(),
						"삭제된 난 묶음 chain은 BASELINE 또는 CREATE로 시작해야 합니다."));
			}
			OrchidGroupMutationEntry last = entries.getLast();
			if (last.getEntryKind() != OrchidGroupMutationEntryKind.DELETE || last.getAfterState() != null) {
				issues.add(issue("MISSING_DELETE_TOMBSTONE", "FARM", groupId.toString(),
						"현재 행이 없는 난 묶음 chain은 DELETE로 종료되어야 합니다."));
			}
		}
	}

	private boolean isCoverageBaseline(OrchidGroupMutationEntry entry, OrchidGroupLedgerCoverage coverage) {
		return coverage != null && entry.getEntryKind() == OrchidGroupMutationEntryKind.BASELINE
				&& entry.getMutation().getMutationType() == OrchidGroupMutationType.BASELINE_IMPORT
				&& entry.getMutation().getSourceDomain() == OrchidGroupMutationSourceDomain.MIGRATION
				&& coverage.getCutoverKey().toString().equals(entry.getMutation().getSourceReferenceId());
	}

	private List<BaselineFingerprintEntry> baselineEntries(OrchidGroupLedgerCoverage coverage,
			Map<Long, List<OrchidGroupMutationEntry>> entriesByGroupId) {
		if (coverage == null) {
			return List.of();
		}
		return entriesByGroupId.values()
			.stream()
			.flatMap(List::stream)
			.filter(entry -> isCoverageBaseline(entry, coverage))
			.sorted(Comparator.comparing(OrchidGroupMutationEntry::getOrchidGroupId))
			.map(entry -> new BaselineFingerprintEntry(entry.getOrchidGroupId(),
					canonicalSnapshot(entry.getAfterState())))
			.toList();
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

	private OrchidGroupLedgerReconciliationIssue groupIssue(String code, OrchidGroupLedgerReconciliationGroup group,
			String message) {
		return OrchidGroupLedgerReconciliationIssue.group(code, group.orchidGroupId(), message);
	}

	private OrchidGroupLedgerReconciliationIssue issue(String code, String domain, String referenceId, String message) {
		return new OrchidGroupLedgerReconciliationIssue(code, domain, referenceId, message);
	}

	private record BaselineFingerprintPayload(UUID cutoverKey, java.time.LocalDate effectiveBusinessDate,
			List<BaselineFingerprintEntry> groups) {
	}

	private record BaselineFingerprintEntry(Long orchidGroupId, OrchidGroupStateSnapshot snapshot) {
	}

	private record CurrentStateFingerprintPayload(List<CurrentStateFingerprintEntry> groups) {
	}

	private record CurrentStateFingerprintEntry(Long orchidGroupId, Long stateRevision,
			OrchidGroupStateSnapshot snapshot) {
	}

}
