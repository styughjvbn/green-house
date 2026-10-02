package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupUsageInspector;
import com.greenhouse.backend.farm.application.orchid.mutation.CompensateTransformMutationsCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEffectiveHeadPolicy;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.repository.collection.OrchidGroupCollectionMemberRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRelationRepository;
import com.greenhouse.backend.work.application.operation.StructureChangeVoidPort;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
@RequiredArgsConstructor
public class FarmStructureChangeVoidAdapter implements StructureChangeVoidPort {

	private final OrchidGroupMutationEntryRepository entryRepository;

	private final OrchidGroupMutationRelationRepository relationRepository;

	private final OrchidGroupRepository orchidGroupRepository;

	private final OrchidGroupCollectionMemberRepository collectionMemberRepository;

	private final List<OrchidGroupUsageInspector> usageInspectors;

	private final OrchidGroupMutationEngine mutationEngine;

	private final OrchidGroupMutationEffectiveHeadPolicy effectiveHeadPolicy;

	private final Clock clock;

	@Override
	@Transactional(readOnly = true)
	public Inspection inspect(Long workOperationId, List<Long> mutationIds) {
		var blockers = new ArrayList<Blocker>();
		List<OrchidGroupMutationEntry> entries = mutationIds.isEmpty() ? List.of()
				: entryRepository.findByMutationIdInOrderByMutationIdAscIdAsc(mutationIds);
		if (mutationIds.isEmpty()
				|| entries.stream().map(entry -> entry.getMutation().getId()).distinct().count() != mutationIds.size()
				|| entries.stream().anyMatch(entry -> !isVoidableType(entry.getMutation().getMutationType()))) {
			blockers
				.add(new Blocker("MUTATION_NOT_REVERSIBLE", "연속 상태 원장이 있는 구조 변경·자리 이동과 연관 선별 폐기만 자동 취소할 수 있습니다.", 1));
		}
		if (!mutationIds.isEmpty() && relationRepository.existsByRelatedMutationIdInAndRelationType(mutationIds,
				OrchidGroupMutationRelationType.COMPENSATES)) {
			blockers.add(new Blocker("ALREADY_COMPENSATED", "이미 보상된 Mutation이 포함되어 있습니다.", 1));
		}
		var grouped = entries.stream()
			.collect(Collectors.groupingBy(OrchidGroupMutationEntry::getOrchidGroupId, java.util.LinkedHashMap::new,
					Collectors.toList()));
		if (grouped.values().stream().anyMatch(this::hasBrokenStateChain)) {
			blockers.add(new Blocker("REPEATED_GROUP_EFFECT", "같은 난 묶음의 작업 Mutation이 하나의 연속 상태 체인을 이루지 않습니다.", 1));
		}
		Set<Long> resultIds = grouped.entrySet()
			.stream()
			.filter(entry -> earliest(entry.getValue()).getBeforeState() == null)
			.map(java.util.Map.Entry::getKey)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		if (!resultIds.isEmpty()) {
			usageInspectors.stream()
				.flatMap(inspector -> inspector.inspect(resultIds, workOperationId).stream())
				.forEach(usage -> blockers.add(new Blocker(usage.code(), usage.message(), usage.count())));
		}
		var groups = orchidGroupRepository.findAllById(grouped.keySet())
			.stream()
			.collect(Collectors.toMap(group -> group.getId(), group -> group));
		List<OrchidGroupMutationEntry> latestEntries = grouped.values().stream().map(this::latest).toList();
		long changed = effectiveHeadPolicy.countGroupsNotAtEffectiveHead(latestEntries, groups);
		if (changed > 0) {
			blockers.add(new Blocker("DOWNSTREAM_MUTATION", "상쇄되지 않은 후속 변경이 있는 난 묶음이 있습니다.", changed));
		}
		List<Long> sourceIds = grouped.entrySet()
			.stream()
			.filter(entry -> earliest(entry.getValue()).getBeforeState() != null)
			.map(java.util.Map.Entry::getKey)
			.sorted()
			.toList();
		return new Inspection(sourceIds, resultIds.stream().toList(), blockers);
	}

	@Override
	public Long compensate(Long workOperationId, String requestKey, List<Long> mutationIds, LocalDate businessDate,
			String reason) {
		var inspection = inspect(workOperationId, mutationIds);
		if (!inspection.blockers().isEmpty()) {
			throw new IllegalArgumentException(inspection.blockers().getFirst().message());
		}
		var compensation = mutationEngine.compensateTransforms(new CompensateTransformMutationsCommand(
				OrchidGroupMutationSources.work(workOperationId, "VOID:" + requestKey), mutationIds, businessDate,
				reason));
		collectionMemberRepository.findByOrchidGroupIdInAndRemovedAtIsNull(inspection.resultOrchidGroupIds())
			.forEach(member -> member.remove(TimeConfig.utcNow(clock)));
		return compensation.mutationId();
	}

	private boolean isVoidableType(OrchidGroupMutationType type) {
		return type == OrchidGroupMutationType.TRANSFORM || type == OrchidGroupMutationType.MOVE
				|| type == OrchidGroupMutationType.DISCARD;
	}

	private boolean hasBrokenStateChain(List<OrchidGroupMutationEntry> entries) {
		List<OrchidGroupMutationEntry> ordered = entries.stream()
			.sorted(java.util.Comparator.comparing(OrchidGroupMutationEntry::getStateRevisionAfter))
			.toList();
		for (int index = 1; index < ordered.size(); index++) {
			var previous = ordered.get(index - 1);
			var current = ordered.get(index);
			if (!previous.getStateRevisionAfter().equals(current.getStateRevisionBefore())
					|| current.getBeforeState() == null || previous.getAfterState() == null
					|| !previous.getAfterState().canonical().equals(current.getBeforeState().canonical())) {
				return true;
			}
		}
		return false;
	}

	private OrchidGroupMutationEntry earliest(List<OrchidGroupMutationEntry> entries) {
		return entries.stream()
			.min(java.util.Comparator.comparing(OrchidGroupMutationEntry::getStateRevisionAfter))
			.orElseThrow();
	}

	private OrchidGroupMutationEntry latest(List<OrchidGroupMutationEntry> entries) {
		return entries.stream()
			.max(java.util.Comparator.comparing(OrchidGroupMutationEntry::getStateRevisionAfter))
			.orElseThrow();
	}

}
