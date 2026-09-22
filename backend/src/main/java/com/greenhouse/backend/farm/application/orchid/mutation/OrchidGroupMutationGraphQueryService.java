package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineage;
import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineageRelationType;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupMutationGraphEdgeResponse;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupMutationGraphEdgeType;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupMutationGraphLocationResponse;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupMutationGraphNodeResponse;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupMutationGraphNodeType;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupMutationGraphResponse;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupMutationStateResponse;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRelationRepository;
import com.greenhouse.backend.farm.repository.structure.BedZoneLocationRow;
import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import com.greenhouse.backend.farm.repository.transformation.OrchidGroupLineageRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrchidGroupMutationGraphQueryService {

	private static final int MAX_DEPTH = 3;

	private static final int MIN_NODES = 10;

	private static final int MAX_NODES = 300;

	private final OrchidGroupMutationEntryRepository entryRepository;

	private final OrchidGroupMutationRelationRepository relationRepository;

	private final OrchidGroupLineageRepository lineageRepository;

	private final BedZoneRepository bedZoneRepository;

	public OrchidGroupMutationGraphResponse getGraph(Long rootOrchidGroupId, int depth, int maxNodes) {
		validate(rootOrchidGroupId, depth, maxNodes);
		Discovery discovery = discoverEntries(rootOrchidGroupId, depth, maxNodes);
		return assemble(rootOrchidGroupId, depth, maxNodes, discovery);
	}

	private Discovery discoverEntries(Long rootOrchidGroupId, int depth, int maxNodes) {
		Map<Long, OrchidGroupMutationEntry> entriesById = new LinkedHashMap<>();
		Set<Long> visitedGroupIds = new LinkedHashSet<>();
		Set<Long> frontier = new LinkedHashSet<>();
		frontier.add(rootOrchidGroupId);
		boolean truncated = false;

		for (int hop = 0; hop <= depth && !frontier.isEmpty(); hop++) {
			visitedGroupIds.addAll(frontier);
			var chainSlice = entryRepository.findGraphEntriesByOrchidGroupIdIn(frontier, PageRequest.of(0, maxNodes));
			chainSlice.forEach(entry -> entriesById.putIfAbsent(entry.getId(), entry));
			truncated |= chainSlice.hasNext();
			if (hop == depth) {
				break;
			}

			Set<Long> transformMutationIds = new LinkedHashSet<>();
			chainSlice.forEach(entry -> {
				if (entry.getMutation().getMutationType() == OrchidGroupMutationType.TRANSFORM) {
					transformMutationIds.add(entry.getMutation().getId());
				}
			});
			if (transformMutationIds.isEmpty()) {
				frontier = Set.of();
				continue;
			}

			var transformSlice = entryRepository.findGraphEntriesByMutationIdIn(transformMutationIds,
					PageRequest.of(0, maxNodes));
			transformSlice.forEach(entry -> entriesById.putIfAbsent(entry.getId(), entry));
			truncated |= transformSlice.hasNext();
			Set<Long> nextFrontier = new LinkedHashSet<>();
			transformSlice.forEach(entry -> {
				if (!visitedGroupIds.contains(entry.getOrchidGroupId())) {
					nextFrontier.add(entry.getOrchidGroupId());
				}
			});
			frontier = nextFrontier;
		}
		return new Discovery(List.copyOf(entriesById.values()), truncated);
	}

	private OrchidGroupMutationGraphResponse assemble(Long rootOrchidGroupId, int depth, int maxNodes,
			Discovery discovery) {
		Map<Long, List<OrchidGroupMutationEntry>> entriesByMutationId = new LinkedHashMap<>();
		discovery.entries()
			.forEach(entry -> entriesByMutationId
				.computeIfAbsent(entry.getMutation().getId(), ignored -> new ArrayList<>())
				.add(entry));

		Map<String, OrchidGroupMutationGraphNodeResponse> nodes = new LinkedHashMap<>();
		Map<Long, List<OrchidGroupMutationEntry>> visibleEntriesByMutationId = new LinkedHashMap<>();
		Map<Long, BedZoneLocationRow> locationsByBedZoneId = locations(discovery.entries());
		boolean truncated = discovery.truncated();
		for (var mutationEntries : entriesByMutationId.values()) {
			Set<String> candidateNodeIds = candidateNodeIds(mutationEntries);
			long newNodeCount = candidateNodeIds.stream().filter(id -> !nodes.containsKey(id)).count();
			if (nodes.size() + newNodeCount > maxNodes) {
				truncated = true;
				continue;
			}
			addNodes(nodes, mutationEntries, locationsByBedZoneId);
			visibleEntriesByMutationId.put(mutationEntries.getFirst().getMutation().getId(), mutationEntries);
		}

		Set<Long> visibleMutationIds = visibleEntriesByMutationId.keySet();
		Map<LineageKey, OrchidGroupLineageRelationType> lineageTypes = lineageTypes(visibleMutationIds);
		List<OrchidGroupMutationGraphEdgeResponse> edges = new ArrayList<>();
		visibleEntriesByMutationId.values()
			.forEach(entries -> entries.forEach(entry -> addStateEdges(edges, entry, lineageTypes)));
		if (!visibleMutationIds.isEmpty()) {
			relationRepository.findConnectedToMutationIds(visibleMutationIds)
				.stream()
				.filter(relation -> visibleMutationIds
					.containsAll(List.of(relation.getMutation().getId(), relation.getRelatedMutation().getId())))
				.forEach(relation -> edges.add(new OrchidGroupMutationGraphEdgeResponse(
						"mutation-relation-" + relation.getId(), mutationNodeId(relation.getMutation().getId()),
						mutationNodeId(relation.getRelatedMutation().getId()),
						OrchidGroupMutationGraphEdgeType.MUTATION_RELATION, null, relation.getRelationType(), null)));
		}

		return new OrchidGroupMutationGraphResponse(rootOrchidGroupId, depth, maxNodes, truncated,
				List.copyOf(nodes.values()), List.copyOf(edges));
	}

	private Set<String> candidateNodeIds(List<OrchidGroupMutationEntry> entries) {
		Set<String> ids = new LinkedHashSet<>();
		ids.add(mutationNodeId(entries.getFirst().getMutation().getId()));
		entries.forEach(entry -> {
			if (entry.getStateRevisionBefore() != null) {
				ids.add(stateNodeId(entry.getOrchidGroupId(), entry.getStateRevisionBefore()));
			}
			ids.add(stateNodeId(entry.getOrchidGroupId(), entry.getStateRevisionAfter()));
		});
		return ids;
	}

	private void addNodes(Map<String, OrchidGroupMutationGraphNodeResponse> nodes,
			List<OrchidGroupMutationEntry> entries, Map<Long, BedZoneLocationRow> locationsByBedZoneId) {
		OrchidGroupMutation mutation = entries.getFirst().getMutation();
		nodes.putIfAbsent(mutationNodeId(mutation.getId()), mutationNode(mutation));
		entries.forEach(entry -> {
			if (entry.getStateRevisionBefore() != null) {
				nodes.putIfAbsent(stateNodeId(entry.getOrchidGroupId(), entry.getStateRevisionBefore()),
						stateNode(entry, entry.getStateRevisionBefore(), entry.getBeforeState(), locationsByBedZoneId));
			}
			nodes.putIfAbsent(stateNodeId(entry.getOrchidGroupId(), entry.getStateRevisionAfter()),
					stateNode(entry, entry.getStateRevisionAfter(), entry.getAfterState(), locationsByBedZoneId));
		});
	}

	private OrchidGroupMutationGraphNodeResponse mutationNode(OrchidGroupMutation mutation) {
		return new OrchidGroupMutationGraphNodeResponse(mutationNodeId(mutation.getId()),
				OrchidGroupMutationGraphNodeType.MUTATION, null, null, null, null, mutation.getId(),
				mutation.getMutationType(), mutation.getSourceDomain(), mutation.getSourceType(),
				mutation.getSourceReferenceId(), mutation.getEffectiveBusinessDate(), mutation.getOccurredAt(), null,
				null);
	}

	private OrchidGroupMutationGraphNodeResponse stateNode(OrchidGroupMutationEntry entry, Long revision,
			OrchidGroupStateSnapshot state, Map<Long, BedZoneLocationRow> locationsByBedZoneId) {
		OrchidGroupStateSnapshot locationState = state != null ? state : entry.getBeforeState();
		return new OrchidGroupMutationGraphNodeResponse(stateNodeId(entry.getOrchidGroupId(), revision),
				OrchidGroupMutationGraphNodeType.STATE, entry.getOrchidGroupId(), revision,
				OrchidGroupMutationStateResponse.from(state), location(locationState, locationsByBedZoneId), null, null,
				null, null, null, null, null, entry.getEntryKind(), entry.getRole());
	}

	private Map<Long, BedZoneLocationRow> locations(List<OrchidGroupMutationEntry> entries) {
		Set<Long> bedZoneIds = new LinkedHashSet<>();
		entries.forEach(entry -> {
			addBedZoneId(bedZoneIds, entry.getBeforeState());
			addBedZoneId(bedZoneIds, entry.getAfterState());
		});
		Map<Long, BedZoneLocationRow> result = new LinkedHashMap<>();
		if (!bedZoneIds.isEmpty()) {
			bedZoneRepository.findLocationRowsByIdIn(bedZoneIds).forEach(row -> result.put(row.id(), row));
		}
		return result;
	}

	private void addBedZoneId(Set<Long> bedZoneIds, OrchidGroupStateSnapshot state) {
		if (state != null && state.bedZoneId() != null) {
			bedZoneIds.add(state.bedZoneId());
		}
	}

	private OrchidGroupMutationGraphLocationResponse location(OrchidGroupStateSnapshot state,
			Map<Long, BedZoneLocationRow> locationsByBedZoneId) {
		if (state == null) {
			return null;
		}
		BedZoneLocationRow row = locationsByBedZoneId.get(state.bedZoneId());
		return new OrchidGroupMutationGraphLocationResponse(row == null ? null : row.houseNumber(),
				row == null ? null : row.physicalBedNumber(), row == null ? null : row.side(),
				row == null ? null : row.bedZoneName(), state.startPosition(), state.endPosition());
	}

	private void addStateEdges(List<OrchidGroupMutationGraphEdgeResponse> edges, OrchidGroupMutationEntry entry,
			Map<LineageKey, OrchidGroupLineageRelationType> lineageTypes) {
		Long mutationId = entry.getMutation().getId();
		if (entry.getStateRevisionBefore() != null) {
			edges.add(new OrchidGroupMutationGraphEdgeResponse("state-input-" + entry.getId(),
					stateNodeId(entry.getOrchidGroupId(), entry.getStateRevisionBefore()), mutationNodeId(mutationId),
					OrchidGroupMutationGraphEdgeType.STATE_INPUT, entry.getRole(), null, null));
		}
		edges.add(new OrchidGroupMutationGraphEdgeResponse("state-output-" + entry.getId(), mutationNodeId(mutationId),
				stateNodeId(entry.getOrchidGroupId(), entry.getStateRevisionAfter()),
				OrchidGroupMutationGraphEdgeType.STATE_OUTPUT, entry.getRole(), null,
				entry.getRole() == OrchidGroupMutationEntryRole.RESULT
						? lineageTypes.get(new LineageKey(mutationId, entry.getOrchidGroupId())) : null));
	}

	private Map<LineageKey, OrchidGroupLineageRelationType> lineageTypes(Collection<Long> mutationIds) {
		Map<LineageKey, OrchidGroupLineageRelationType> result = new LinkedHashMap<>();
		if (mutationIds.isEmpty()) {
			return result;
		}
		for (OrchidGroupLineage lineage : lineageRepository.findByMutationIdInOrderByIdAsc(mutationIds)) {
			result.putIfAbsent(new LineageKey(lineage.getMutationId(), lineage.getResultOrchidGroup().getId()),
					lineage.getRelationType());
		}
		return result;
	}

	private String mutationNodeId(Long mutationId) {
		return "mutation-" + mutationId;
	}

	private String stateNodeId(Long orchidGroupId, Long revision) {
		return "group-" + orchidGroupId + "-revision-" + revision;
	}

	private void validate(Long rootOrchidGroupId, int depth, int maxNodes) {
		if (rootOrchidGroupId == null || rootOrchidGroupId < 1) {
			throw new IllegalArgumentException("난 묶음 ID는 1 이상이어야 합니다.");
		}
		if (depth < 0 || depth > MAX_DEPTH) {
			throw new IllegalArgumentException("그래프 조회 깊이는 0~3이어야 합니다.");
		}
		if (maxNodes < MIN_NODES || maxNodes > MAX_NODES) {
			throw new IllegalArgumentException("그래프 노드 수는 10~300이어야 합니다.");
		}
	}

	private record Discovery(List<OrchidGroupMutationEntry> entries, boolean truncated) {
	}

	private record LineageKey(Long mutationId, Long resultOrchidGroupId) {
	}

}
