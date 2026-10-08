package com.greenhouse.backend.farm.orchid.integration;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRelationRepository;
import com.greenhouse.backend.farm.repository.structure.BedZoneLocationRow;
import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import com.greenhouse.backend.work.spi.operation.WorkOperationMutationGraphPort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FarmWorkOperationMutationGraphAdapter implements WorkOperationMutationGraphPort {

  private final OrchidGroupMutationEntryRepository entryRepository;

  private final OrchidGroupMutationRelationRepository relationRepository;

  private final BedZoneRepository bedZoneRepository;

  @Override
  public Fragment load(
      Collection<Long> mutationIds, boolean expandLineage, int depth, int maxNodes) {
    if (mutationIds.isEmpty()) {
      return Fragment.empty();
    }
    Discovery discovery;
    if (expandLineage) {
      discovery = discover(mutationIds, depth, maxNodes);
    } else {
      var slice =
          entryRepository.findGraphEntriesByMutationIdIn(mutationIds, PageRequest.of(0, maxNodes));
      discovery = new Discovery(slice.getContent(), slice.hasNext());
    }
    return assemble(discovery, maxNodes);
  }

  private Discovery discover(Collection<Long> rootMutationIds, int depth, int maxNodes) {
    Map<Long, OrchidGroupMutationEntry> entries = new LinkedHashMap<>();
    Set<Long> mutationFrontier = new LinkedHashSet<>(rootMutationIds);
    Set<Long> visitedMutations = new LinkedHashSet<>();
    boolean truncated = false;
    for (int hop = 0; hop <= depth && !mutationFrontier.isEmpty(); hop++) {
      var mutationSlice =
          entryRepository.findGraphEntriesByMutationIdIn(
              mutationFrontier, PageRequest.of(0, maxNodes));
      mutationSlice.forEach(entry -> entries.putIfAbsent(entry.getId(), entry));
      truncated |= mutationSlice.hasNext();
      visitedMutations.addAll(mutationFrontier);
      if (hop == depth) {
        break;
      }
      Set<Long> groupIds = new LinkedHashSet<>();
      mutationSlice.forEach(entry -> groupIds.add(entry.getOrchidGroupId()));
      if (groupIds.isEmpty()) {
        break;
      }
      var chainSlice =
          entryRepository.findGraphEntriesByOrchidGroupIdIn(groupIds, PageRequest.of(0, maxNodes));
      chainSlice.forEach(entry -> entries.putIfAbsent(entry.getId(), entry));
      truncated |= chainSlice.hasNext();
      Set<Long> next = new LinkedHashSet<>();
      chainSlice.forEach(
          entry -> {
            Long mutationId = entry.getMutation().getId();
            if (!visitedMutations.contains(mutationId)) {
              next.add(mutationId);
            }
          });
      mutationFrontier = next;
    }
    return new Discovery(List.copyOf(entries.values()), truncated);
  }

  private Fragment assemble(Discovery discovery, int maxNodes) {
    Map<Long, List<OrchidGroupMutationEntry>> entriesByMutation = new LinkedHashMap<>();
    discovery
        .entries()
        .forEach(
            entry ->
                entriesByMutation
                    .computeIfAbsent(entry.getMutation().getId(), ignored -> new ArrayList<>())
                    .add(entry));
    Set<String> selectedStateIds = new LinkedHashSet<>();
    Map<Long, List<OrchidGroupMutationEntry>> visibleEntries = new LinkedHashMap<>();
    List<MutationNode> mutations = new ArrayList<>();
    Map<String, StateNode> states = new LinkedHashMap<>();
    List<Edge> edges = new ArrayList<>();
    boolean truncated = discovery.truncated();
    Set<Long> visibleMutationIds = new LinkedHashSet<>();
    for (var mutationEntries : entriesByMutation.values()) {
      int candidateCount = 1;
      Set<String> candidateStateIds = new LinkedHashSet<>();
      for (var entry : mutationEntries) {
        if (entry.getStateRevisionBefore() != null) {
          candidateStateIds.add(stateId(entry.getOrchidGroupId(), entry.getStateRevisionBefore()));
        }
        candidateStateIds.add(stateId(entry.getOrchidGroupId(), entry.getStateRevisionAfter()));
      }
      candidateCount +=
          (int) candidateStateIds.stream().filter(id -> !selectedStateIds.contains(id)).count();
      if (mutations.size() + selectedStateIds.size() + candidateCount > maxNodes) {
        truncated = true;
        continue;
      }
      var mutation = mutationEntries.getFirst().getMutation();
      visibleMutationIds.add(mutation.getId());
      mutations.add(
          new MutationNode(
              mutation.getId(),
              mutation.getMutationType().name(),
              mutation.getEffectiveBusinessDate(),
              mutation.getOccurredAt()));
      selectedStateIds.addAll(candidateStateIds);
      visibleEntries.put(mutation.getId(), mutationEntries);
    }
    var locations =
        locations(visibleEntries.values().stream().flatMap(Collection::stream).toList());
    for (var mutationEntries : visibleEntries.values()) {
      var mutation = mutationEntries.getFirst().getMutation();
      for (var entry : mutationEntries) {
        if (entry.getStateRevisionBefore() != null) {
          String beforeId = stateId(entry.getOrchidGroupId(), entry.getStateRevisionBefore());
          states.putIfAbsent(
              beforeId,
              stateNode(
                  beforeId,
                  entry.getOrchidGroupId(),
                  entry.getStateRevisionBefore(),
                  entry.getBeforeState(),
                  locations));
          edges.add(
              new Edge(
                  "state-input-" + entry.getId(),
                  beforeId,
                  mutationId(mutation.getId()),
                  "STATE_INPUT",
                  entry.getRole().name()));
        }
        String afterId = stateId(entry.getOrchidGroupId(), entry.getStateRevisionAfter());
        states.putIfAbsent(
            afterId,
            stateNode(
                afterId,
                entry.getOrchidGroupId(),
                entry.getStateRevisionAfter(),
                entry.getAfterState(),
                locations));
        edges.add(
            new Edge(
                "state-output-" + entry.getId(),
                mutationId(mutation.getId()),
                afterId,
                "STATE_OUTPUT",
                entry.getRole().name()));
      }
    }
    if (!visibleMutationIds.isEmpty()) {
      var relations =
          relationRepository.findVisibleGraphRelations(
              visibleMutationIds, PageRequest.of(0, maxNodes * 4));
      truncated |= relations.hasNext();
      relations.stream()
          .filter(
              relation ->
                  visibleMutationIds.contains(relation.getMutation().getId())
                      && visibleMutationIds.contains(relation.getRelatedMutation().getId()))
          .forEach(
              relation ->
                  edges.add(
                      new Edge(
                          "mutation-relation-" + relation.getId(),
                          mutationId(relation.getMutation().getId()),
                          mutationId(relation.getRelatedMutation().getId()),
                          "MUTATION_RELATION",
                          relation.getRelationType().name())));
    }
    return new Fragment(
        truncated, List.copyOf(mutations), List.copyOf(states.values()), List.copyOf(edges));
  }

  private Map<Long, BedZoneLocationRow> locations(List<OrchidGroupMutationEntry> entries) {
    Set<Long> ids = new LinkedHashSet<>();
    entries.forEach(
        entry -> {
          if (entry.getBeforeState() != null && entry.getBeforeState().bedZoneId() != null)
            ids.add(entry.getBeforeState().bedZoneId());
          if (entry.getAfterState() != null && entry.getAfterState().bedZoneId() != null)
            ids.add(entry.getAfterState().bedZoneId());
        });
    Map<Long, BedZoneLocationRow> result = new LinkedHashMap<>();
    if (!ids.isEmpty())
      bedZoneRepository.findLocationRowsByIdIn(ids).forEach(row -> result.put(row.id(), row));
    return result;
  }

  private StateNode stateNode(
      String id,
      Long orchidGroupId,
      Long revision,
      OrchidGroupStateSnapshot snapshot,
      Map<Long, BedZoneLocationRow> locations) {
    if (snapshot == null) return new StateNode(id, orchidGroupId, revision, null);
    BedZoneLocationRow location = locations.get(snapshot.bedZoneId());
    return new StateNode(
        id,
        orchidGroupId,
        revision,
        new State(
            snapshot.quantity(),
            snapshot.reservedQuantity(),
            snapshot.status(),
            snapshot.varietyId(),
            snapshot.genus(),
            snapshot.varietyName(),
            snapshot.ageYear(),
            snapshot.potSizeCode(),
            snapshot.bedZoneId(),
            location == null ? null : location.houseNumber(),
            location == null ? null : location.physicalBedNumber(),
            location == null ? null : location.bedZoneName(),
            snapshot.startPosition(),
            snapshot.endPosition()));
  }

  private String mutationId(Long id) {
    return "mutation-" + id;
  }

  private String stateId(Long groupId, Long revision) {
    return "group-" + groupId + "-revision-" + revision;
  }

  private record Discovery(List<OrchidGroupMutationEntry> entries, boolean truncated) {}
}
