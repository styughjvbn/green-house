package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupLedgerCoverageRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRepository;
import com.greenhouse.backend.farm.repository.transformation.OrchidGroupLineageRepository;
import com.greenhouse.backend.migration.application.orchid.OrchidGroupHistoryMigrationRunPhase;
import com.greenhouse.backend.migration.application.orchid.OrchidGroupHistoryMigrationRunPlan;
import com.greenhouse.backend.migration.application.orchid.OrchidGroupHistoryMigrationRunService;
import com.greenhouse.backend.migration.application.orchid.OrchidGroupHistoryMigrationRunState;
import com.greenhouse.backend.work.application.effect.WorkHistoricalEffectService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ORCHID-CUTOVER: TRANSITION_ONLY — Historical Mutation을 검증하고 batch로 적재한다.
 * Removal gate: 최종 historical catch-up과 검증 완료.
 */
@Service
@RequiredArgsConstructor
public class OrchidGroupHistoryMigrationService {

	private static final int ENGINE_SCHEMA_VERSION = 1;

	private final OrchidGroupHistoryMigrationRunService runService;
	private final OrchidGroupMutationEntryRepository entryRepository;
	private final OrchidGroupMutationRepository mutationRepository;
	private final OrchidGroupRepository orchidGroupRepository;
	private final OrchidGroupLedgerCoverageRepository coverageRepository;
	private final OrchidGroupMutationFingerprint fingerprint;
	private final OrchidGroupLedgerReconciliationService reconciliationService;
	private final WorkHistoricalEffectService workHistoricalEffectService;
	private final OrchidGroupLineageRepository lineageRepository;
	private final Clock clock;

	@Transactional
	public Long plan(OrchidGroupHistoryMigrationPlanCommand command) {
		var reconciliation = requirePreBaselineReady();
		return runService.registerPlan(new OrchidGroupHistoryMigrationRunPlan(
				command.runKey(),
				command.sourceCutoff(),
				command.backupFingerprint(),
				command.manifestFingerprint(),
				reconciliation.currentStateFingerprint(),
				command.effectiveBusinessDate(),
				command.sourceCounts(),
				command.plannedCounts()), Instant.now(clock));
	}

	@Transactional
	public void start(UUID runKey) {
		requirePreBaselineReady();
		runService.start(runKey, Instant.now(clock));
	}

	@Transactional
	public OrchidGroupHistoryMigrationBatchResult importBatch(
			UUID runKey,
			List<OrchidGroupHistoricalMutationInput> requestedCandidates) {
		requirePreBaselineReady();
		OrchidGroupHistoryMigrationRunState run = runService.lock(runKey);
		boolean replayOnly = run.phase() == OrchidGroupHistoryMigrationRunPhase.IMPORTED
				|| run.phase() == OrchidGroupHistoryMigrationRunPhase.VERIFIED;
		if (run.phase() != OrchidGroupHistoryMigrationRunPhase.PREPARING && !replayOnly) {
			throw new ConflictException("시작된 historical migration에서만 batch를 적재할 수 있습니다.");
		}
		List<OrchidGroupHistoricalMutationInput> candidates = sortedAndValidatedCandidates(
				run, requestedCandidates);
		validateGroupsExist(candidates);

		int imported = 0;
		int replayed = 0;
		int entryCount = 0;
		List<OrchidGroupHistoryMigrationImportedSource> importedSources = new ArrayList<>();
		Instant recordedAt = Instant.now(clock);
		for (OrchidGroupHistoricalMutationInput candidate : candidates) {
			String mutationFingerprint = fingerprint.calculate(candidate);
			var existing = mutationRepository
					.findBySourceDomainAndSourceTypeAndSourceReferenceIdAndSourceOperationKey(
							candidate.source().domain(),
							candidate.source().type(),
							candidate.source().referenceId(),
							candidate.source().operationKey());
			if (existing.isPresent()) {
				validateReplay(run, existing.get(), candidate, mutationFingerprint);
				importedSources.add(new OrchidGroupHistoryMigrationImportedSource(
						candidate.source(), existing.get().getId()));
				replayed++;
				entryCount += candidate.entries().size();
				continue;
			}
			if (replayOnly) {
				throw new ConflictException("완료된 historical migration에는 새 source를 추가할 수 없습니다.");
			}

			OrchidGroupMutation mutation = mutationRepository.save(new OrchidGroupMutation(
					candidate.mutationType(),
					candidate.source(),
					mutationFingerprint,
					candidate.occurredAt(),
					recordedAt,
					candidate.effectiveBusinessDate(),
					candidate.reason(),
					ENGINE_SCHEMA_VERSION));
			List<OrchidGroupMutationEntry> entries = candidate.entries().stream()
					.map(input -> OrchidGroupMutationEntry.historical(
							mutation, run.id(), input.orchidGroupId(), input.role()))
					.toList();
			entryRepository.saveAll(entries);
			importedSources.add(new OrchidGroupHistoryMigrationImportedSource(
					candidate.source(), mutation.getId()));
			imported++;
			entryCount += entries.size();
		}
		return new OrchidGroupHistoryMigrationBatchResult(
				runKey, candidates.size(), imported, replayed, entryCount, importedSources);
	}

	@Transactional
	public Map<String, Long> completeImport(UUID runKey) {
		OrchidGroupHistoryMigrationRunState run = runService.lock(runKey);
		if (run.phase() == OrchidGroupHistoryMigrationRunPhase.IMPORTED
				|| run.phase() == OrchidGroupHistoryMigrationRunPhase.VERIFIED) {
			return run.importedCounts();
		}
		if (run.phase() != OrchidGroupHistoryMigrationRunPhase.PREPARING) {
			throw new ConflictException("PREPARING historical migration만 적재 완료할 수 있습니다.");
		}
		Map<String, Long> actualCounts = actualCounts(run.id());
		validatePlannedCounts(run, actualCounts);
		runService.completeImport(runKey, actualCounts, Instant.now(clock));
		return actualCounts;
	}

	@Transactional
	public Map<String, Object> verify(UUID runKey) {
		OrchidGroupHistoryMigrationRunState run = runService.lock(runKey);
		if (run.phase() == OrchidGroupHistoryMigrationRunPhase.VERIFIED) {
			return run.verificationResult();
		}
		if (run.phase() != OrchidGroupHistoryMigrationRunPhase.IMPORTED) {
			throw new ConflictException("IMPORTED historical migration만 검증할 수 있습니다.");
		}
		var reconciliation = requirePreBaselineReady();
		Map<String, Long> actualCounts = actualCounts(run.id());
		validatePlannedCounts(run, actualCounts);
		if (!run.sourceStateFingerprint().equals(reconciliation.currentStateFingerprint())) {
			throw new ConflictException("Historical import 중 현재 난 묶음 상태가 변경되었습니다.");
		}
		long mutationWithoutEntry = mutationRepository.countWithoutEntries();
		if (mutationWithoutEntry != 0) {
			throw new ConflictException("Entry가 없는 Mutation이 존재합니다.");
		}
		List<Long> unlinkedWorkEffectIds = workHistoricalEffectService.findUnlinkedIds(run.sourceCutoff());
		if (!unlinkedWorkEffectIds.isEmpty()) {
			throw new ConflictException("Mutation에 연결되지 않은 historical Work 효과가 존재합니다: "
					+ unlinkedWorkEffectIds);
		}
		long unlinkedLineageCount = lineageRepository.countUnlinkedHistorical(
				LocalDateTime.ofInstant(run.sourceCutoff(), ZoneOffset.UTC));
		if (unlinkedLineageCount != 0) {
			throw new ConflictException("Mutation에 연결되지 않은 historical Lineage가 존재합니다: "
					+ unlinkedLineageCount);
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("ready", true);
		result.put("currentStateFingerprint", reconciliation.currentStateFingerprint());
		result.put("mutations", actualCounts.get(OrchidGroupHistoryMigrationPlanCommand.MUTATIONS));
		result.put("entries", actualCounts.get(OrchidGroupHistoryMigrationPlanCommand.ENTRIES));
		result.put("mutationWithoutEntry", mutationWithoutEntry);
		result.put("unlinkedWorkEffects", 0);
		result.put("unlinkedLineages", unlinkedLineageCount);
		runService.verify(runKey, result, Instant.now(clock));
		return Map.copyOf(result);
	}

	@Transactional(readOnly = true)
	public void validateCutoverReady(OrchidGroupLedgerReconciliationReport report) {
		runService.findLatest().ifPresent(run -> {
			if (run.phase() != OrchidGroupHistoryMigrationRunPhase.VERIFIED) {
				throw new ConflictException("완료되지 않은 historical migration run이 존재합니다.");
			}
			if (!run.sourceStateFingerprint().equals(report.currentStateFingerprint())) {
				throw new ConflictException("Historical migration 검증 후 현재 난 묶음 상태가 변경되었습니다.");
			}
		});
	}

	private OrchidGroupLedgerReconciliationReport requirePreBaselineReady() {
		if (coverageRepository.count() != 0) {
			throw new ConflictException("Historical migration은 baseline coverage 생성 전에만 실행할 수 있습니다.");
		}
		OrchidGroupLedgerReconciliationReport report = reconciliationService.reconcile();
		if (report.stage() != OrchidGroupLedgerReconciliationStage.PRE_BASELINE || !report.ready()) {
			throw new ConflictException("Historical migration 전에 PRE_BASELINE 대사를 통과해야 합니다.");
		}
		return report;
	}

	private List<OrchidGroupHistoricalMutationInput> sortedAndValidatedCandidates(
			OrchidGroupHistoryMigrationRunState run,
			List<OrchidGroupHistoricalMutationInput> requestedCandidates) {
		if (requestedCandidates == null || requestedCandidates.isEmpty()) {
			throw new IllegalArgumentException("Historical migration batch가 비어 있습니다.");
		}
		Set<String> sourceKeys = new HashSet<>();
		for (OrchidGroupHistoricalMutationInput candidate : requestedCandidates) {
			if (candidate.occurredAt().isAfter(run.sourceCutoff())) {
				throw new ConflictException("source cutoff 이후의 historical Mutation은 적재할 수 없습니다.");
			}
			String sourceKey = candidate.source().domain() + ":" + candidate.source().type() + ":"
					+ candidate.source().referenceId() + ":" + candidate.source().operationKey();
			if (!sourceKeys.add(sourceKey)) {
				throw new IllegalArgumentException("Historical migration batch의 source identity가 중복됩니다.");
			}
		}
		return requestedCandidates.stream()
				.sorted(Comparator.comparing(OrchidGroupHistoricalMutationInput::occurredAt)
						.thenComparing(candidate -> candidate.source().domain().name())
						.thenComparing(candidate -> candidate.source().type())
						.thenComparing(candidate -> candidate.source().referenceId())
						.thenComparing(candidate -> candidate.source().operationKey()))
				.toList();
	}

	private void validateGroupsExist(List<OrchidGroupHistoricalMutationInput> candidates) {
		Set<Long> groupIds = new HashSet<>();
		candidates.forEach(candidate -> candidate.entries().forEach(input ->
				groupIds.add(input.orchidGroupId())));
		Set<Long> existingIds = new HashSet<>();
		orchidGroupRepository.findAllById(groupIds).forEach(group -> existingIds.add(group.getId()));
		if (!existingIds.equals(groupIds)) {
			Set<Long> missing = new HashSet<>(groupIds);
			missing.removeAll(existingIds);
			throw new NotFoundException("Historical Entry 대상 난 묶음을 찾을 수 없습니다: " + missing);
		}
	}

	private void validateReplay(
			OrchidGroupHistoryMigrationRunState run,
			OrchidGroupMutation mutation,
			OrchidGroupHistoricalMutationInput candidate,
			String mutationFingerprint) {
		if (!mutation.hasSameCommandFingerprint(mutationFingerprint)) {
			throw new ConflictException("같은 historical source identity의 payload가 변경되었습니다.");
		}
		List<OrchidGroupMutationEntry> existingEntries = entryRepository
				.findByMutationIdOrderByOrchidGroupIdAsc(mutation.getId());
		Map<Long, OrchidGroupMutationEntry> byGroupId = new LinkedHashMap<>();
		existingEntries.forEach(entry -> byGroupId.put(entry.getOrchidGroupId(), entry));
		if (existingEntries.size() != candidate.entries().size()
				|| existingEntries.stream().anyMatch(entry ->
						!run.id().equals(entry.getMigrationRunId()))) {
			throw new ConflictException("Historical source identity가 다른 migration run 또는 Entry와 연결되어 있습니다.");
		}
		for (OrchidGroupHistoricalEntryInput input : candidate.entries()) {
			OrchidGroupMutationEntry existing = byGroupId.get(input.orchidGroupId());
			if (existing == null || existing.getRole() != input.role()) {
				throw new ConflictException("Historical source identity의 Entry 구성이 변경되었습니다.");
			}
		}
	}

	private Map<String, Long> actualCounts(Long runId) {
		Map<String, Long> counts = new LinkedHashMap<>();
		counts.put(
				OrchidGroupHistoryMigrationPlanCommand.MUTATIONS,
				entryRepository.countDistinctMutationsByMigrationRunId(runId));
		counts.put(
				OrchidGroupHistoryMigrationPlanCommand.ENTRIES,
				entryRepository.countByMigrationRunId(runId));
		return Map.copyOf(counts);
	}

	private void validatePlannedCounts(
			OrchidGroupHistoryMigrationRunState run,
			Map<String, Long> actualCounts) {
		List<String> mismatches = new ArrayList<>();
		for (String key : List.of(
				OrchidGroupHistoryMigrationPlanCommand.MUTATIONS,
				OrchidGroupHistoryMigrationPlanCommand.ENTRIES)) {
			if (!actualCounts.get(key).equals(run.plannedCounts().get(key))) {
				mismatches.add(key + " expected=" + run.plannedCounts().get(key)
						+ " actual=" + actualCounts.get(key));
			}
		}
		if (!mismatches.isEmpty()) {
			throw new ConflictException("Historical migration plan과 적재 건수가 다릅니다: "
					+ String.join(", ", mismatches));
		}
	}
}
