package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshotFactory;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRelationRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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

  public boolean hasBrokenCompensationChain(Collection<OrchidGroupMutationEntry> selectedEntries) {
    var selectedByGroup =
        selectedEntries.stream()
            .collect(Collectors.groupingBy(OrchidGroupMutationEntry::getOrchidGroupId));
    var gaps = new ArrayList<ChainGap>();
    for (var selected : selectedByGroup.values()) {
      var ordered =
          selected.stream()
              .sorted(Comparator.comparing(OrchidGroupMutationEntry::getStateRevisionAfter))
              .toList();
      for (int index = 1; index < ordered.size(); index++) {
        var previous = ordered.get(index - 1);
        var next = ordered.get(index);
        if (!sameState(previous.getAfterState(), next.getBeforeState())
            || previous.getStateRevisionAfter() > next.getStateRevisionBefore()) {
          return true;
        }
        if (!previous.getStateRevisionAfter().equals(next.getStateRevisionBefore())) {
          gaps.add(new ChainGap(previous, next));
        }
      }
    }
    if (gaps.isEmpty()) {
      return false;
    }
    var chains =
        entryRepository.findStateChainByOrchidGroupIdIn(selectedByGroup.keySet()).stream()
            .collect(Collectors.groupingBy(OrchidGroupMutationEntry::getOrchidGroupId));
    var segmentIds = new ArrayList<Set<Long>>();
    Set<Long> allIds = new LinkedHashSet<>();
    for (var gap : gaps) {
      var segment =
          chains.getOrDefault(gap.previous().getOrchidGroupId(), List.of()).stream()
              .filter(
                  entry ->
                      entry.getStateRevisionAfter() > gap.previous().getStateRevisionAfter()
                          && entry.getStateRevisionAfter() <= gap.next().getStateRevisionBefore())
              .sorted(Comparator.comparing(OrchidGroupMutationEntry::getStateRevisionAfter))
              .toList();
      var previous = gap.previous();
      Set<Long> ids = new LinkedHashSet<>();
      for (var entry : segment) {
        if (!previous.getStateRevisionAfter().equals(entry.getStateRevisionBefore())
            || !sameState(previous.getAfterState(), entry.getBeforeState())) {
          return true;
        }
        ids.add(entry.getMutation().getId());
        previous = entry;
      }
      if (ids.isEmpty()
          || !previous.getStateRevisionAfter().equals(gap.next().getStateRevisionBefore())
          || !sameState(previous.getAfterState(), gap.next().getBeforeState())) {
        return true;
      }
      segmentIds.add(ids);
      allIds.addAll(ids);
    }
    var relations =
        relationRepository.findConnectedToMutationIds(allIds).stream()
            .filter(
                relation ->
                    relation.getRelationType() == OrchidGroupMutationRelationType.COMPENSATES)
            .toList();
    return segmentIds.stream().anyMatch(ids -> !neutralized(ids, relations).containsAll(ids));
  }

  private boolean sameState(OrchidGroupStateSnapshot left, OrchidGroupStateSnapshot right) {
    return left != null && right != null && left.canonical().equals(right.canonical());
  }

  private Set<Long> neutralized(Set<Long> ids, List<OrchidGroupMutationRelation> relations) {
    Set<Long> result = new LinkedHashSet<>();
    for (var relation : relations) {
      Long compensationId = relation.getMutation().getId();
      Long originalId = relation.getRelatedMutation().getId();
      if (ids.contains(compensationId) && ids.contains(originalId)) {
        result.add(compensationId);
        result.add(originalId);
      }
    }
    return result;
  }

  private record ChainGap(OrchidGroupMutationEntry previous, OrchidGroupMutationEntry next) {}

  public long countGroupsNotAtEffectiveHead(
      Collection<OrchidGroupMutationEntry> targetEntries, Map<Long, OrchidGroup> currentGroups) {
    if (targetEntries.isEmpty()) {
      return 0;
    }
    Map<Long, OrchidGroupMutationEntry> targetByGroupId =
        targetEntries.stream()
            .collect(Collectors.toMap(OrchidGroupMutationEntry::getOrchidGroupId, entry -> entry));
    Map<Long, List<OrchidGroupMutationEntry>> chainByGroupId =
        entryRepository.findStateChainByOrchidGroupIdIn(targetByGroupId.keySet()).stream()
            .collect(
                Collectors.groupingBy(
                    OrchidGroupMutationEntry::getOrchidGroupId,
                    LinkedHashMap::new,
                    Collectors.toList()));
    Map<Long, Set<Long>> downstreamMutationIdsByGroupId = new LinkedHashMap<>();
    Set<Long> allDownstreamMutationIds = new LinkedHashSet<>();
    targetByGroupId.forEach(
        (groupId, target) -> {
          Set<Long> mutationIds =
              chainByGroupId.getOrDefault(groupId, List.of()).stream()
                  .filter(entry -> entry.getStateRevisionAfter() > target.getStateRevisionAfter())
                  .map(entry -> entry.getMutation().getId())
                  .collect(Collectors.toCollection(LinkedHashSet::new));
          downstreamMutationIdsByGroupId.put(groupId, mutationIds);
          allDownstreamMutationIds.addAll(mutationIds);
        });

    List<OrchidGroupMutationRelation> compensationRelations =
        allDownstreamMutationIds.isEmpty()
            ? List.of()
            : relationRepository.findConnectedToMutationIds(allDownstreamMutationIds).stream()
                .filter(
                    relation ->
                        relation.getRelationType() == OrchidGroupMutationRelationType.COMPENSATES)
                .toList();

    return targetByGroupId.entrySet().stream()
        .filter(
            target -> {
              OrchidGroup current = currentGroups.get(target.getKey());
              if (current == null
                  || !target
                      .getValue()
                      .getAfterState()
                      .canonical()
                      .equals(OrchidGroupStateSnapshotFactory.from(current).canonical())) {
                return true;
              }
              Set<Long> downstreamMutationIds = downstreamMutationIdsByGroupId.get(target.getKey());
              Set<Long> neutralizedMutationIds =
                  neutralized(downstreamMutationIds, compensationRelations);
              return !neutralizedMutationIds.containsAll(downstreamMutationIds);
            })
        .count();
  }
}
