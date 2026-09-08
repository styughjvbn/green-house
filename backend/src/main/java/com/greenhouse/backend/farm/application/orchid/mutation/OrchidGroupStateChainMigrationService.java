package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverage;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverageStatus;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryKind;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineage;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupLedgerCoverageRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRepository;
import com.greenhouse.backend.farm.repository.transformation.OrchidGroupLineageRepository;
import com.greenhouse.backend.work.application.effect.WorkOrchidGroupStateChainMigrationService;
import com.greenhouse.backend.work.application.effect.WorkStateChainMutationLinkCommand;
import com.greenhouse.backend.work.application.effect.WorkStateChainMutationSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ORCHID-CUTOVER: RECOVERY — complete state-chain manifest를 원자적으로 적재한다. Removal gate: 운영
 * cutover 완료 및 사후 복구 도구 보존 정책 확정.
 */
@Service
@RequiredArgsConstructor
public class OrchidGroupStateChainMigrationService {

	private static final int ENGINE_SCHEMA_VERSION = 1;

	private static final int MANIFEST_SCHEMA_VERSION = 2;

	private static final String GENERATED_FROM = "orchid_state_chain_manifest_normalizer";

	private final OrchidGroupLedgerCoverageRepository coverageRepository;

	private final OrchidGroupLedgerPreparationService preparationService;

	private final OrchidGroupRepository orchidGroupRepository;

	private final OrchidGroupMutationRepository mutationRepository;

	private final OrchidGroupMutationEntryRepository entryRepository;

	private final OrchidGroupLineageRepository lineageRepository;

	private final WorkOrchidGroupStateChainMigrationService workMigrationService;

	private final OrchidGroupMutationFingerprint fingerprint;

	private final OrchidGroupLedgerReconciliationService reconciliationService;

	private final Clock clock;

	@Transactional(readOnly = true)
	public OrchidGroupStateChainMigrationResult validate(UUID cutoverKey, LocalDate effectiveBusinessDate,
			String minimumWriterVersion, String manifestFingerprint, OrchidGroupStateChainMigrationManifest manifest) {
		validateOperatorInput(cutoverKey, effectiveBusinessDate, minimumWriterVersion, manifestFingerprint, manifest);
		ResolvedManifest resolved = resolveAndValidate(cutoverKey, manifest);
		List<OrchidGroup> groups = orchidGroupRepository.findAll()
			.stream()
			.sorted(Comparator.comparing(OrchidGroup::getId))
			.toList();
		validateCurrentState(resolved, groups, true);
		return result(cutoverKey, false, resolved, 0, 0, reconciliationService.reconcile());
	}

	@Transactional
	public OrchidGroupStateChainMigrationResult importManifest(UUID cutoverKey, LocalDate effectiveBusinessDate,
			String minimumWriterVersion, String manifestFingerprint, OrchidGroupStateChainMigrationManifest manifest) {
		validateOperatorInput(cutoverKey, effectiveBusinessDate, minimumWriterVersion, manifestFingerprint, manifest);
		ResolvedManifest resolved = resolveAndValidate(cutoverKey, manifest);

		OrchidGroupLedgerReconciliationReport initialReport = reconciliationService.reconcile();
		OrchidGroupLedgerCoverage coverage = prepareCoverage(cutoverKey, effectiveBusinessDate, minimumWriterVersion,
				manifestFingerprint, initialReport);
		List<OrchidGroup> groups = orchidGroupRepository.findAllForUpdateByIdIn(resolved.currentGroupIds());
		validateCurrentState(resolved, groups, true);

		Map<String, Long> mutationIdsByKey = new LinkedHashMap<>();
		List<WorkStateChainMutationLinkCommand> workLinks = new ArrayList<>();
		int imported = 0;
		int replayed = 0;
		Instant recordedAt = Instant.now(clock);
		for (ResolvedMutation candidate : resolved.mutations()) {
			String commandFingerprint = fingerprint.calculate(candidate.manifestMutation());
			var existing = mutationRepository.findBySourceDomainAndSourceTypeAndSourceReferenceIdAndSourceOperationKey(
					candidate.source().domain(), candidate.source().type(), candidate.source().referenceId(),
					candidate.source().operationKey());
			OrchidGroupMutation mutation;
			if (existing.isPresent()) {
				mutation = existing.get();
				validateReplay(mutation, candidate, commandFingerprint);
				replayed++;
			}
			else {
				mutation = mutationRepository.save(new OrchidGroupMutation(candidate.manifestMutation().mutationType(),
						candidate.source(), commandFingerprint, candidate.manifestMutation().occurredAt(), recordedAt,
						candidate.manifestMutation().effectiveBusinessDate(), reason(candidate.manifestMutation()),
						ENGINE_SCHEMA_VERSION));
				entryRepository.saveAll(toEntries(mutation, candidate.manifestMutation().entries()));
				imported++;
			}
			mutationIdsByKey.put(candidate.manifestMutation().mutationKey(), mutation.getId());
			if (candidate.workEffectId() != null) {
				workLinks.add(new WorkStateChainMutationLinkCommand(candidate.workEffectId(), mutation.getId(),
						candidate.source().correlationId()));
			}
		}

		workMigrationService.linkMutations(workLinks);
		linkLineages(resolved, mutationIdsByKey);
		applyCurrentRevisions(resolved, groups);
		coverage.claimImport(manifestFingerprint);
		coverageRepository.flush();
		entryRepository.flush();

		OrchidGroupLedgerReconciliationReport report = reconciliationService.reconcile();
		if (!report.ready() || report.stage() != OrchidGroupLedgerReconciliationStage.BASELINE_PREPARING
				&& report.stage() != OrchidGroupLedgerReconciliationStage.ACTIVE) {
			throw new ConflictException("Complete state-chain 적재 후 ledger 대사를 통과하지 못했습니다.");
		}
		return result(cutoverKey, true, resolved, imported, replayed, report);
	}

	private OrchidGroupLedgerCoverage prepareCoverage(UUID cutoverKey, LocalDate effectiveBusinessDate,
			String minimumWriterVersion, String manifestFingerprint,
			OrchidGroupLedgerReconciliationReport initialReport) {
		var existing = coverageRepository.findByCutoverKey(cutoverKey);
		if (existing.isEmpty()) {
			if (!initialReport.ready() || initialReport.stage() != OrchidGroupLedgerReconciliationStage.PRE_BASELINE) {
				throw new ConflictException("Complete state-chain 적재 전에 PRE_BASELINE 대사를 통과해야 합니다.");
			}
			preparationService.prepare(cutoverKey, effectiveBusinessDate, minimumWriterVersion);
			preparationService.startImport(cutoverKey);
		}
		else {
			preparationService.validatePreparation(cutoverKey, effectiveBusinessDate, minimumWriterVersion);
			if (existing.get().getStatus() == OrchidGroupLedgerCoverageStatus.FAILED) {
				throw new ConflictException("실패한 cutover coverage에는 state-chain을 적재할 수 없습니다.");
			}
			if (existing.get().getImportStartedAt() == null) {
				preparationService.startImport(cutoverKey);
			}
		}
		OrchidGroupLedgerCoverage coverage = coverageRepository.findForUpdateByCutoverKey(cutoverKey)
			.orElseThrow(() -> new NotFoundException("OrchidGroup ledger coverage를 찾을 수 없습니다."));
		coverage.claimImport(manifestFingerprint);
		return coverage;
	}

	private ResolvedManifest resolveAndValidate(UUID cutoverKey, OrchidGroupStateChainMigrationManifest manifest) {
		Set<Long> workEffectIds = manifest.mutations()
			.stream()
			.filter(mutation -> "WORK_EFFECT".equals(mutation.sourceType()))
			.map(this::singleWorkEffectId)
			.collect(Collectors.toCollection(HashSet::new));
		Map<Long, WorkStateChainMutationSource> workSources = workMigrationService.resolveSources(workEffectIds);

		Set<String> mutationKeys = new HashSet<>();
		Set<String> sourceKeys = new HashSet<>();
		Set<Long> lineageIds = new HashSet<>();
		List<ResolvedMutation> resolvedMutations = new ArrayList<>();
		Map<Long, List<OrchidGroupStateChainMigrationManifest.Entry>> entriesByGroup = new LinkedHashMap<>();
		for (var mutation : manifest.mutations()) {
			if (mutation.mutationKey() == null || mutation.mutationKey().isBlank()
					|| !mutationKeys.add(mutation.mutationKey())) {
				throw new ConflictException("State-chain manifest mutation_key가 비었거나 중복됩니다.");
			}
			if (mutation.mutationType() == null || mutation.occurredAt() == null
					|| mutation.effectiveBusinessDate() == null || mutation.entries().isEmpty()) {
				throw new ConflictException("State-chain manifest Mutation 필수 값이 누락되었습니다.");
			}
			Long workEffectId = null;
			OrchidGroupMutationSource source;
			if ("WORK_EFFECT".equals(mutation.sourceType())) {
				workEffectId = singleWorkEffectId(mutation);
				WorkStateChainMutationSource workSource = workSources.get(workEffectId);
				source = OrchidGroupMutationSources.work(workSource.workOperationId(), workSource.effectKey());
			}
			else if (mutation.mutationType() == OrchidGroupMutationType.BASELINE_IMPORT) {
				source = new OrchidGroupMutationSource(OrchidGroupMutationSourceDomain.MIGRATION, "LEDGER_BASELINE",
						cutoverKey.toString(), mutation.mutationKey(), cutoverKey);
			}
			else {
				source = OrchidGroupMutationSources.migration(mutation.sourceType(), mutation.sourceReference(),
						mutation.mutationKey());
			}
			String sourceKey = source.domain() + ":" + source.type() + ":" + source.referenceId() + ":"
					+ source.operationKey();
			if (!sourceKeys.add(sourceKey)) {
				throw new ConflictException("State-chain manifest source identity가 중복됩니다: " + sourceKey);
			}
			Set<Long> mutationGroupIds = new HashSet<>();
			for (var entry : mutation.entries()) {
				if (entry.orchidGroupId() == null || entry.entryKind() == null || entry.role() == null
						|| !mutationGroupIds.add(entry.orchidGroupId())) {
					throw new ConflictException("State-chain Mutation의 Entry가 누락되거나 중복됩니다: " + mutation.mutationKey());
				}
				entriesByGroup.computeIfAbsent(entry.orchidGroupId(), ignored -> new ArrayList<>()).add(entry);
			}
			List<Long> mutationLineageIds = evidenceIds(mutation, "lineage_ids");
			for (Long lineageId : mutationLineageIds) {
				if (!lineageIds.add(lineageId)) {
					throw new ConflictException("State-chain manifest lineage ID가 중복됩니다: " + lineageId);
				}
			}
			resolvedMutations.add(new ResolvedMutation(mutation, source, workEffectId, mutationLineageIds));
		}

		validateChains(entriesByGroup);
		Set<Long> currentGroupIds = entriesByGroup.entrySet()
			.stream()
			.filter(entry -> entry.getValue()
				.stream()
				.max(Comparator.comparing(OrchidGroupStateChainMigrationManifest.Entry::revisionAfter))
				.orElseThrow()
				.entryKind() != OrchidGroupMutationEntryKind.DELETE)
			.map(Map.Entry::getKey)
			.collect(Collectors.toCollection(HashSet::new));
		return new ResolvedManifest(List.copyOf(resolvedMutations), copyChains(entriesByGroup),
				Set.copyOf(currentGroupIds), Set.copyOf(lineageIds));
	}

	private void validateChains(Map<Long, List<OrchidGroupStateChainMigrationManifest.Entry>> entriesByGroup) {
		for (var groupChain : entriesByGroup.entrySet()) {
			List<OrchidGroupStateChainMigrationManifest.Entry> entries = groupChain.getValue()
				.stream()
				.sorted(Comparator.comparing(OrchidGroupStateChainMigrationManifest.Entry::revisionAfter))
				.toList();
			OrchidGroupStateChainMigrationManifest.Entry previous = null;
			for (int index = 0; index < entries.size(); index++) {
				var entry = entries.get(index);
				if (index == 0) {
					boolean baseline = entry.entryKind() == OrchidGroupMutationEntryKind.BASELINE
							&& entry.revisionBefore() == null && Long.valueOf(0).equals(entry.revisionAfter());
					boolean create = entry.entryKind() == OrchidGroupMutationEntryKind.CREATE
							&& entry.revisionBefore() == null && Long.valueOf(1).equals(entry.revisionAfter());
					if ((!baseline && !create) || entry.beforeState() != null || entry.afterState() == null) {
						throw chainConflict(groupChain.getKey(), "최초 Entry 규칙이 올바르지 않습니다.");
					}
				}
				else {
					if (!Objects.equals(previous.revisionAfter(), entry.revisionBefore())
							|| !Long.valueOf(entry.revisionBefore() + 1).equals(entry.revisionAfter())
							|| !sameSnapshot(previous.afterState(), entry.beforeState())) {
						throw chainConflict(groupChain.getKey(), "revision 또는 snapshot이 연속되지 않습니다.");
					}
					boolean delete = entry.entryKind() == OrchidGroupMutationEntryKind.DELETE;
					if (delete != (entry.afterState() == null) || delete && index != entries.size() - 1) {
						throw chainConflict(groupChain.getKey(), "DELETE는 afterState가 없는 terminal Entry여야 합니다.");
					}
					if (!delete && entry.entryKind() != OrchidGroupMutationEntryKind.CHANGE) {
						throw chainConflict(groupChain.getKey(), "중간 Entry는 CHANGE여야 합니다.");
					}
				}
				previous = entry;
			}
		}
	}

	private void validateCurrentState(ResolvedManifest resolved, List<OrchidGroup> groups,
			boolean allowImportedRevision) {
		Map<Long, OrchidGroup> groupsById = groups.stream()
			.collect(Collectors.toMap(OrchidGroup::getId, group -> group));
		if (!groupsById.keySet().equals(resolved.currentGroupIds())) {
			Set<Long> missing = new HashSet<>(resolved.currentGroupIds());
			missing.removeAll(groupsById.keySet());
			Set<Long> unexpected = new HashSet<>(groupsById.keySet());
			unexpected.removeAll(resolved.currentGroupIds());
			throw new ConflictException(
					"Manifest 최종 난 묶음 집합이 현재 DB와 다릅니다. missing=" + missing + " unexpected=" + unexpected);
		}
		for (Long groupId : resolved.currentGroupIds().stream().sorted().toList()) {
			OrchidGroup group = groupsById.get(groupId);
			var last = lastEntry(resolved.entriesByGroup().get(groupId));
			if (!sameSnapshot(OrchidGroupStateSnapshot.from(group), last.afterState())) {
				throw new ConflictException("Manifest 마지막 snapshot이 현재 난 묶음과 다릅니다: " + groupId);
			}
			if (group.getStateRevision() != null
					&& (!allowImportedRevision || !group.getStateRevision().equals(last.revisionAfter()))) {
				throw new ConflictException("현재 난 묶음 revision이 manifest와 다릅니다: " + groupId);
			}
		}
	}

	private List<OrchidGroupMutationEntry> toEntries(OrchidGroupMutation mutation,
			List<OrchidGroupStateChainMigrationManifest.Entry> inputs) {
		return inputs.stream().map(input -> switch (input.entryKind()) {
			case BASELINE ->
				OrchidGroupMutationEntry.baseline(mutation, input.orchidGroupId(), input.afterState().canonical());
			case CREATE -> OrchidGroupMutationEntry.created(mutation, input.orchidGroupId(), input.role(),
					input.afterState().canonical());
			case CHANGE -> OrchidGroupMutationEntry.changed(mutation, input.orchidGroupId(), input.role(),
					input.revisionBefore(), input.beforeState().canonical(), input.afterState().canonical());
			case DELETE -> OrchidGroupMutationEntry.deleted(mutation, input.orchidGroupId(), input.role(),
					input.revisionBefore(), input.beforeState().canonical());
		}).toList();
	}

	private void validateReplay(OrchidGroupMutation mutation, ResolvedMutation candidate, String commandFingerprint) {
		if (!mutation.hasSameCommandFingerprint(commandFingerprint)
				|| mutation.getMutationType() != candidate.manifestMutation().mutationType()) {
			throw new ConflictException("같은 state-chain source identity의 payload가 변경되었습니다.");
		}
		List<OrchidGroupMutationEntry> existing = entryRepository
			.findByMutationIdOrderByOrchidGroupIdAsc(mutation.getId());
		List<OrchidGroupStateChainMigrationManifest.Entry> requested = candidate.manifestMutation()
			.entries()
			.stream()
			.sorted(Comparator.comparing(OrchidGroupStateChainMigrationManifest.Entry::orchidGroupId))
			.toList();
		if (existing.size() != requested.size()) {
			throw new ConflictException("State-chain source identity의 Entry 수가 변경되었습니다.");
		}
		for (int index = 0; index < existing.size(); index++) {
			OrchidGroupMutationEntry saved = existing.get(index);
			var input = requested.get(index);
			if (!saved.getOrchidGroupId().equals(input.orchidGroupId()) || saved.getEntryKind() != input.entryKind()
					|| saved.getRole() != input.role()
					|| !Objects.equals(saved.getStateRevisionBefore(), input.revisionBefore())
					|| !Objects.equals(saved.getStateRevisionAfter(), input.revisionAfter())
					|| !sameSnapshot(saved.getBeforeState(), input.beforeState())
					|| !sameSnapshot(saved.getAfterState(), input.afterState())) {
				throw new ConflictException("State-chain source identity의 Entry 구성이 변경되었습니다.");
			}
		}
	}

	private void applyCurrentRevisions(ResolvedManifest resolved, List<OrchidGroup> groups) {
		Map<Long, OrchidGroup> groupsById = groups.stream()
			.collect(Collectors.toMap(OrchidGroup::getId, group -> group));
		for (Long groupId : resolved.currentGroupIds().stream().sorted().toList()) {
			OrchidGroup group = groupsById.get(groupId);
			List<OrchidGroupStateChainMigrationManifest.Entry> chain = resolved.entriesByGroup()
				.get(groupId)
				.stream()
				.sorted(Comparator.comparing(OrchidGroupStateChainMigrationManifest.Entry::revisionAfter))
				.toList();
			long finalRevision = chain.getLast().revisionAfter();
			if (group.getStateRevision() != null) {
				if (group.getStateRevision() != finalRevision) {
					throw new ConflictException("이미 설정된 난 묶음 revision이 manifest와 다릅니다: " + groupId);
				}
				continue;
			}
			if (chain.getFirst().entryKind() == OrchidGroupMutationEntryKind.BASELINE) {
				group.establishBaselineRevision();
			}
			else {
				group.establishCreationRevision();
			}
			while (group.getStateRevision() < finalRevision) {
				group.advanceStateRevision();
			}
		}
	}

	private void linkLineages(ResolvedManifest resolved, Map<String, Long> mutationIdsByKey) {
		if (resolved.lineageIds().isEmpty()) {
			return;
		}
		List<OrchidGroupLineage> lineages = lineageRepository.findAllById(resolved.lineageIds());
		if (lineages.size() != resolved.lineageIds().size()) {
			throw new NotFoundException("State-chain manifest의 Lineage를 모두 찾을 수 없습니다.");
		}
		Map<Long, OrchidGroupLineage> byId = lineages.stream()
			.collect(Collectors.toMap(OrchidGroupLineage::getId, lineage -> lineage));
		for (ResolvedMutation mutation : resolved.mutations()) {
			Long mutationId = mutationIdsByKey.get(mutation.manifestMutation().mutationKey());
			for (Long lineageId : mutation.lineageIds()) {
				byId.get(lineageId).linkMutation(mutationId);
			}
		}
	}

	private void validateOperatorInput(UUID cutoverKey, LocalDate effectiveBusinessDate, String minimumWriterVersion,
			String manifestFingerprint, OrchidGroupStateChainMigrationManifest manifest) {
		if (cutoverKey == null || effectiveBusinessDate == null || manifest == null || minimumWriterVersion == null
				|| minimumWriterVersion.isBlank()) {
			throw new IllegalArgumentException("State-chain migration 필수 입력이 누락되었습니다.");
		}
		if (manifestFingerprint == null || !manifestFingerprint.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("State-chain manifest fingerprint 형식이 올바르지 않습니다.");
		}
		if (manifest.manifestSchemaVersion() != MANIFEST_SCHEMA_VERSION
				|| !GENERATED_FROM.equals(manifest.generatedFrom()) || !manifest.migrationReady()
				|| !manifest.blockingIssues().isEmpty() || manifest.mutations().isEmpty()) {
			throw new ConflictException("적재할 수 없는 state-chain manifest입니다.");
		}
	}

	private Long singleWorkEffectId(OrchidGroupStateChainMigrationManifest.Mutation mutation) {
		List<Long> ids = evidenceIds(mutation, "work_effect_ids");
		if (ids.size() != 1) {
			throw new ConflictException("WORK_EFFECT Mutation은 정확히 하나의 Work 효과를 참조해야 합니다: " + mutation.mutationKey());
		}
		return ids.getFirst();
	}

	private List<Long> evidenceIds(OrchidGroupStateChainMigrationManifest.Mutation mutation, String key) {
		Object value = mutation.evidence().get(key);
		if (value == null) {
			return List.of();
		}
		if (!(value instanceof List<?> values)) {
			throw new ConflictException("State-chain evidence ID 목록 형식이 올바르지 않습니다: " + key);
		}
		try {
			return values.stream().map(item -> Long.valueOf(item.toString())).toList();
		}
		catch (NumberFormatException exception) {
			throw new ConflictException("State-chain evidence ID 형식이 올바르지 않습니다: " + key);
		}
	}

	private OrchidGroupStateChainMigrationManifest.Entry lastEntry(
			List<OrchidGroupStateChainMigrationManifest.Entry> entries) {
		return entries.stream()
			.max(Comparator.comparing(OrchidGroupStateChainMigrationManifest.Entry::revisionAfter))
			.orElseThrow();
	}

	private boolean sameSnapshot(OrchidGroupStateSnapshot first, OrchidGroupStateSnapshot second) {
		return Objects.equals(first == null ? null : first.canonical(), second == null ? null : second.canonical());
	}

	private ConflictException chainConflict(Long groupId, String message) {
		return new ConflictException("난 묶음 " + groupId + " state-chain 오류: " + message);
	}

	private String reason(OrchidGroupStateChainMigrationManifest.Mutation mutation) {
		return mutation.reason() == null || mutation.reason().isBlank()
				? "Complete OrchidGroup state-chain migration: " + mutation.sourceType() : mutation.reason();
	}

	private Map<Long, List<OrchidGroupStateChainMigrationManifest.Entry>> copyChains(
			Map<Long, List<OrchidGroupStateChainMigrationManifest.Entry>> source) {
		Map<Long, List<OrchidGroupStateChainMigrationManifest.Entry>> result = new LinkedHashMap<>();
		source.forEach((groupId, entries) -> result.put(groupId, List.copyOf(entries)));
		return Map.copyOf(result);
	}

	private OrchidGroupStateChainMigrationResult result(UUID cutoverKey, boolean applied, ResolvedManifest resolved,
			int imported, int replayed, OrchidGroupLedgerReconciliationReport report) {
		int entryCount = resolved.mutations()
			.stream()
			.mapToInt(mutation -> mutation.manifestMutation().entries().size())
			.sum();
		return new OrchidGroupStateChainMigrationResult(cutoverKey, applied, resolved.mutations().size(), entryCount,
				resolved.currentGroupIds().size(), resolved.entriesByGroup().size() - resolved.currentGroupIds().size(),
				imported, replayed, report);
	}

	private record ResolvedManifest(List<ResolvedMutation> mutations,
			Map<Long, List<OrchidGroupStateChainMigrationManifest.Entry>> entriesByGroup, Set<Long> currentGroupIds,
			Set<Long> lineageIds) {
	}

	private record ResolvedMutation(OrchidGroupStateChainMigrationManifest.Mutation manifestMutation,
			OrchidGroupMutationSource source, Long workEffectId, List<Long> lineageIds) {
	}

}
