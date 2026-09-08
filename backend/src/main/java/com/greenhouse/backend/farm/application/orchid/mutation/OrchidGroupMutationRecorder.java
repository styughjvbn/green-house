package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRelationRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupWriteFenceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Ledger and fence writes participate in the engine caller's transaction. */
@Component
@RequiredArgsConstructor
class OrchidGroupMutationRecorder {

	private static final int MUTATION_SCHEMA_VERSION = 1;

	private final OrchidGroupMutationRepository mutationRepository;

	private final OrchidGroupMutationEntryRepository entryRepository;

	private final OrchidGroupMutationRelationRepository relationRepository;

	private final OrchidGroupWriteFenceRepository writeFenceRepository;

	private final Clock clock;

	OrchidGroupMutation start(OrchidGroupMutationType mutationType, OrchidGroupMutationSource source,
			String commandFingerprint, LocalDate effectiveBusinessDate, String reason) {
		OrchidGroupMutation mutation = mutationRepository.save(new OrchidGroupMutation(mutationType, source,
				commandFingerprint, Instant.now(clock), effectiveBusinessDate, reason, MUTATION_SCHEMA_VERSION));
		writeFenceRepository.authorizeMutation(mutation.getId());
		return mutation;
	}

	OrchidGroupMutationResult created(OrchidGroupMutation mutation, List<OrchidGroup> groups) {
		return saveEntries(mutation, createdEntries(mutation, groups));
	}

	OrchidGroupMutationResult changed(OrchidGroupMutation mutation, List<Change> changes) {
		return saveEntries(mutation,
				changes.stream().map(change -> change.entry(mutation, OrchidGroupMutationEntryRole.AFFECTED)).toList());
	}

	OrchidGroupMutationResult transformed(OrchidGroupMutation mutation, List<Change> sources,
			List<OrchidGroup> results) {
		List<OrchidGroupMutationEntry> entries = new ArrayList<>();
		sources.forEach(change -> entries.add(change.entry(mutation, OrchidGroupMutationEntryRole.SOURCE)));
		entries.addAll(createdEntries(mutation, results));
		return saveEntries(mutation, entries);
	}

	private List<OrchidGroupMutationEntry> createdEntries(OrchidGroupMutation mutation, List<OrchidGroup> groups) {
		return groups.stream()
			.map(group -> OrchidGroupMutationEntry.created(mutation, group.getId(), OrchidGroupMutationEntryRole.RESULT,
					OrchidGroupStateSnapshot.from(group)))
			.toList();
	}

	private OrchidGroupMutationResult saveEntries(OrchidGroupMutation mutation,
			List<OrchidGroupMutationEntry> entries) {
		entryRepository.saveAll(entries);
		return OrchidGroupMutationResult.from(mutation, entries);
	}

	List<OrchidGroupMutation> findRelated(RelatedOrchidGroupMutations relatedMutations, Set<Long> affectedGroupIds,
			Set<OrchidGroupMutationType> allowedMutationTypes) {
		if (relatedMutations == null || relatedMutations.legacySource()) {
			return List.of();
		}
		Map<Long, OrchidGroupMutation> mutationsById = mutationRepository.findAllById(relatedMutations.mutationIds())
			.stream()
			.collect(Collectors.toMap(OrchidGroupMutation::getId, Function.identity()));
		if (mutationsById.size() != relatedMutations.mutationIds().size()) {
			throw new NotFoundException("관련 Mutation을 모두 찾을 수 없습니다.");
		}
		if (allowedMutationTypes.isEmpty() || mutationsById.values()
			.stream()
			.anyMatch(mutation -> !allowedMutationTypes.contains(mutation.getMutationType()))) {
			throw new IllegalArgumentException("관련 Mutation 유형이 보정·보상 대상과 일치하지 않습니다.");
		}
		Map<Long, Set<Long>> groupIdsByMutationId = new LinkedHashMap<>();
		entryRepository.findByMutationIdInOrderByMutationIdAscIdAsc(relatedMutations.mutationIds())
			.forEach(entry -> groupIdsByMutationId
				.computeIfAbsent(entry.getMutation().getId(), ignored -> new LinkedHashSet<>())
				.add(entry.getOrchidGroupId()));
		for (Long mutationId : relatedMutations.mutationIds()) {
			Set<Long> relatedGroupIds = groupIdsByMutationId.getOrDefault(mutationId, Set.of());
			if (relatedGroupIds.stream().noneMatch(affectedGroupIds::contains)) {
				throw new IllegalArgumentException("관련 Mutation은 변경 대상 난 묶음과 연결되어야 합니다.");
			}
		}
		Set<Long> allRelatedGroupIds = groupIdsByMutationId.values()
			.stream()
			.flatMap(Collection::stream)
			.collect(Collectors.toSet());
		if (!allRelatedGroupIds.containsAll(affectedGroupIds)) {
			throw new IllegalArgumentException("모든 변경 대상은 관련 Mutation에 포함되어야 합니다.");
		}
		return relatedMutations.mutationIds().stream().map(mutationsById::get).toList();
	}

	void relate(OrchidGroupMutation mutation, List<OrchidGroupMutation> relatedMutations,
			OrchidGroupMutationRelationType relationType) {
		if (relatedMutations.isEmpty()) {
			return;
		}
		if (relationType == null) {
			throw new IllegalArgumentException("Mutation 관계 유형이 필요합니다.");
		}
		relationRepository.saveAll(relatedMutations.stream()
			.map(related -> new OrchidGroupMutationRelation(mutation, related, relationType))
			.toList());
	}

	record Change(Long groupId, long revisionBefore, OrchidGroupStateSnapshot beforeState,
			OrchidGroupStateSnapshot afterState) {
		OrchidGroupMutationEntry entry(OrchidGroupMutation mutation, OrchidGroupMutationEntryRole role) {
			return OrchidGroupMutationEntry.changed(mutation, groupId, role, revisionBefore, beforeState, afterState);
		}
	}

}
