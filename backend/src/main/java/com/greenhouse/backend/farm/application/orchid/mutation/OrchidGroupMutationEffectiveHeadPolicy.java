package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRelationRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrchidGroupMutationEffectiveHeadPolicy {

	private final OrchidGroupMutationEntryRepository entryRepository;

	private final OrchidGroupMutationRelationRepository relationRepository;

	public long countGroupsNotAtEffectiveHead(Collection<OrchidGroupMutationEntry> targetEntries,
			Map<Long, OrchidGroup> currentGroups) {
		if (targetEntries.isEmpty()) {
			return 0;
		}
		Map<Long, OrchidGroupMutationEntry> targetByGroupId = targetEntries.stream()
			.collect(Collectors.toMap(OrchidGroupMutationEntry::getOrchidGroupId, entry -> entry));
		Map<Long, List<OrchidGroupMutationEntry>> chainByGroupId = entryRepository
			.findStateChainByOrchidGroupIdIn(targetByGroupId.keySet())
			.stream()
			.collect(Collectors.groupingBy(OrchidGroupMutationEntry::getOrchidGroupId, LinkedHashMap::new,
					Collectors.toList()));
		Map<Long, Set<Long>> downstreamMutationIdsByGroupId = new LinkedHashMap<>();
		Set<Long> allDownstreamMutationIds = new LinkedHashSet<>();
		targetByGroupId.forEach((groupId, target) -> {
			Set<Long> mutationIds = chainByGroupId.getOrDefault(groupId, List.of())
				.stream()
				.filter(entry -> entry.getStateRevisionAfter() > target.getStateRevisionAfter())
				.map(entry -> entry.getMutation().getId())
				.collect(Collectors.toCollection(LinkedHashSet::new));
			downstreamMutationIdsByGroupId.put(groupId, mutationIds);
			allDownstreamMutationIds.addAll(mutationIds);
		});

		List<OrchidGroupMutationRelation> compensationRelations = allDownstreamMutationIds.isEmpty() ? List.of()
				: relationRepository.findConnectedToMutationIds(allDownstreamMutationIds)
					.stream()
					.filter(relation -> relation.getRelationType() == OrchidGroupMutationRelationType.COMPENSATES)
					.toList();

		return targetByGroupId.entrySet().stream().filter(target -> {
			OrchidGroup current = currentGroups.get(target.getKey());
			if (current == null || !target.getValue().getAfterState().canonical()
				.equals(OrchidGroupStateSnapshot.from(current).canonical())) {
				return true;
			}
			Set<Long> downstreamMutationIds = downstreamMutationIdsByGroupId.get(target.getKey());
			Set<Long> neutralizedMutationIds = new LinkedHashSet<>();
			compensationRelations.forEach(relation -> {
				Long compensationId = relation.getMutation().getId();
				Long originalId = relation.getRelatedMutation().getId();
				if (downstreamMutationIds.contains(compensationId) && downstreamMutationIds.contains(originalId)) {
					neutralizedMutationIds.add(compensationId);
					neutralizedMutationIds.add(originalId);
				}
			});
			return !neutralizedMutationIds.containsAll(downstreamMutationIds);
		}).count();
	}

}
