package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationRelationType;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import com.greenhouse.backend.work.dto.operation.WorkOperationGraphDetail;
import com.greenhouse.backend.work.dto.operation.WorkOperationGraphEdgeResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationGraphEdgeType;
import com.greenhouse.backend.work.dto.operation.WorkOperationGraphNodeResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationGraphNodeType;
import com.greenhouse.backend.work.dto.operation.WorkOperationGraphResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationGraphStateResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationOriginType;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkOperationCorrectionRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkOperationGraphQueryService {

	private static final int MAX_DEPTH = 3;

	private static final int MIN_NODES = 10;

	private static final int MAX_NODES = 300;

	private final WorkOperationRepository operationRepository;

	private final WorkOperationTargetRepository targetRepository;

	private final WorkAppliedEffectRepository effectRepository;

	private final WorkOperationCorrectionRepository correctionRepository;

	private final WorkOperationRelationSummaryAssembler relationSummaryAssembler;

	private final WorkOperationMutationGraphPort mutationGraphPort;

	public WorkOperationGraphResponse get(Long operationId, WorkOperationGraphDetail detail, int depth, int maxNodes) {
		validate(operationId, detail, depth, maxNodes);
		WorkOperation root = operationRepository.findWithWorkTypeById(operationId)
			.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
		Map<Long, WorkOperation> seedOperations = relatedOperations(root);
		Map<Long, List<Long>> seedMutationIds = detail == WorkOperationGraphDetail.WORK ? Map.of()
				: mutationIds(seedOperations.values());
		List<Long> rootMutationIds = seedMutationIds.values().stream().flatMap(Collection::stream).distinct().toList();
		WorkOperationMutationGraphPort.Fragment fragment = detail == WorkOperationGraphDetail.WORK
				? WorkOperationMutationGraphPort.Fragment.empty()
				: mutationGraphPort.load(rootMutationIds, detail == WorkOperationGraphDetail.LINEAGE, depth, maxNodes);
		Map<Long, WorkOperation> operations = new LinkedHashMap<>(seedOperations);
		Map<Long, Set<Long>> operationIdsByMutation = operationIdsByMutation(seedMutationIds);
		if (detail == WorkOperationGraphDetail.LINEAGE) {
			addDiscoveredOperations(fragment, operations, operationIdsByMutation);
		}
		Map<Long, List<WorkOperationTarget>> targets = targets(operations.keySet());
		var rootRelationSummary = relationSummaryAssembler.assemble(List.of(root)).get(root.getId());
		List<WorkOperationGraphNodeResponse> nodes = new ArrayList<>();
		List<WorkOperationGraphEdgeResponse> edges = new ArrayList<>();

		addOrigin(root, rootRelationSummary, nodes, edges);
		seedOperations.values()
			.forEach(operation -> nodes.add(operationNode(operation, operationId.equals(operation.getId()),
					targets.getOrDefault(operation.getId(), List.of()))));

		boolean truncated = nodes.size() > maxNodes;
		if (truncated) {
			nodes.subList(maxNodes, nodes.size()).clear();
			Set<String> visible = nodes.stream().map(WorkOperationGraphNodeResponse::id).collect(Collectors.toSet());
			edges.removeIf(edge -> !visible.contains(edge.sourceNodeId()) || !visible.contains(edge.targetNodeId()));
		}

		if (detail != WorkOperationGraphDetail.WORK && nodes.size() < maxNodes) {
			truncated |= addMutationFlow(operationId, maxNodes, operations, targets, operationIdsByMutation, fragment,
					nodes, edges);
		}
		Set<Long> visibleOperationIds = nodes.stream()
			.filter(node -> node.nodeType() == WorkOperationGraphNodeType.WORK_OPERATION)
			.map(WorkOperationGraphNodeResponse::workOperationId)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		Map<Long, WorkOperation> visibleOperations = operations.entrySet()
			.stream()
			.filter(entry -> visibleOperationIds.contains(entry.getKey()))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left,
					LinkedHashMap::new));
		addOperationRelations(visibleOperations, edges);

		return new WorkOperationGraphResponse(operationId, detail, depth, maxNodes, truncated, List.copyOf(nodes),
				List.copyOf(edges));
	}

	private void addDiscoveredOperations(WorkOperationMutationGraphPort.Fragment fragment,
			Map<Long, WorkOperation> operations, Map<Long, Set<Long>> operationIdsByMutation) {
		List<Long> mutationIds = fragment.mutations()
			.stream()
			.map(WorkOperationMutationGraphPort.MutationNode::id)
			.toList();
		if (mutationIds.isEmpty()) {
			return;
		}
		effectRepository.findByMutationIdInOrderByMutationIdAscIdAsc(mutationIds).forEach(effect -> {
			Long mutationId = effect.getMutationId();
			WorkOperation operation = effect.getWorkOperation();
			if (mutationId == null || operation == null) {
				return;
			}
			operations.putIfAbsent(operation.getId(), operation);
			operationIdsByMutation.computeIfAbsent(mutationId, ignored -> new LinkedHashSet<>()).add(operation.getId());
		});
		correctionRepository.findByMutationIdIn(mutationIds).forEach(correction -> {
			var operation = correction.getOriginalWorkOperation();
			operations.putIfAbsent(operation.getId(), operation);
			operationIdsByMutation.computeIfAbsent(correction.getMutationId(), ignored -> new LinkedHashSet<>())
				.add(operation.getId());
		});
	}

	private Map<Long, Set<Long>> operationIdsByMutation(Map<Long, List<Long>> mutationIdsByOperation) {
		Map<Long, Set<Long>> result = new LinkedHashMap<>();
		mutationIdsByOperation.forEach((operationId, mutationIds) -> mutationIds.forEach(
				mutationId -> result.computeIfAbsent(mutationId, ignored -> new LinkedHashSet<>()).add(operationId)));
		return result;
	}

	private boolean addMutationFlow(Long selectedOperationId, int maxNodes, Map<Long, WorkOperation> operations,
			Map<Long, List<WorkOperationTarget>> targets, Map<Long, Set<Long>> operationIdsByMutation,
			WorkOperationMutationGraphPort.Fragment fragment, List<WorkOperationGraphNodeResponse> nodes,
			List<WorkOperationGraphEdgeResponse> edges) {
		Map<String, WorkOperationMutationGraphPort.StateNode> stateById = fragment.states()
			.stream()
			.collect(Collectors.toMap(WorkOperationMutationGraphPort.StateNode::id, value -> value));
		Map<String, List<WorkOperationMutationGraphPort.Edge>> stateEdgesByMutation = fragment.edges()
			.stream()
			.filter(edge -> edge.type().equals(WorkOperationGraphEdgeType.STATE_INPUT.name())
					|| edge.type().equals(WorkOperationGraphEdgeType.STATE_OUTPUT.name()))
			.collect(
					Collectors.groupingBy(
							edge -> edge.type().equals(WorkOperationGraphEdgeType.STATE_INPUT.name())
									? edge.targetNodeId() : edge.sourceNodeId(),
							LinkedHashMap::new, Collectors.toList()));
		Set<String> visibleNodeIds = nodes.stream()
			.map(WorkOperationGraphNodeResponse::id)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		Set<Long> visibleMutationIds = new LinkedHashSet<>();
		boolean truncated = fragment.truncated();

		for (var mutation : fragment.mutations()) {
			String mutationNodeId = mutationNodeId(mutation.id());
			Set<String> stateIds = stateEdgesByMutation.getOrDefault(mutationNodeId, List.of())
				.stream()
				.flatMap(edge -> List.of(edge.sourceNodeId(), edge.targetNodeId()).stream())
				.filter(stateById::containsKey)
				.collect(Collectors.toCollection(LinkedHashSet::new));
			Set<Long> newOperationIds = operationIdsByMutation.getOrDefault(mutation.id(), Set.of())
				.stream()
				.filter(id -> !visibleNodeIds.contains(operationNodeId(id)))
				.collect(Collectors.toCollection(LinkedHashSet::new));
			long newStateCount = stateIds.stream().filter(id -> !visibleNodeIds.contains(id)).count();
			int candidateCount = 1 + newOperationIds.size() + Math.toIntExact(newStateCount);
			if (nodes.size() + candidateCount > maxNodes) {
				truncated = true;
				continue;
			}
			for (Long operationId : newOperationIds) {
				WorkOperation operation = operations.get(operationId);
				if (operation == null) {
					continue;
				}
				nodes.add(operationNode(operation, selectedOperationId.equals(operationId),
						targets.getOrDefault(operationId, List.of())));
				visibleNodeIds.add(operationNodeId(operationId));
			}
			nodes.add(mutationNode(mutation));
			visibleNodeIds.add(mutationNodeId);
			visibleMutationIds.add(mutation.id());
			for (String stateId : stateIds) {
				if (visibleNodeIds.add(stateId)) {
					nodes.add(stateNode(stateById.get(stateId)));
				}
			}
		}

		fragment.edges()
			.stream()
			.filter(edge -> visibleNodeIds.contains(edge.sourceNodeId())
					&& visibleNodeIds.contains(edge.targetNodeId()))
			.forEach(edge -> edges.add(new WorkOperationGraphEdgeResponse(edge.id(), edge.sourceNodeId(),
					edge.targetNodeId(), WorkOperationGraphEdgeType.valueOf(edge.type()), edge.relationType())));
		visibleMutationIds.forEach(mutationId -> operationIdsByMutation.getOrDefault(mutationId, Set.of())
			.stream()
			.filter(operationId -> visibleNodeIds.contains(operationNodeId(operationId)))
			.forEach(operationId -> edges.add(new WorkOperationGraphEdgeResponse(
					"effect-" + operationId + "-" + mutationId, operationNodeId(operationId),
					mutationNodeId(mutationId), WorkOperationGraphEdgeType.EFFECT, null))));
		return truncated;
	}

	private WorkOperationGraphNodeResponse mutationNode(WorkOperationMutationGraphPort.MutationNode mutation) {
		return new WorkOperationGraphNodeResponse(mutationNodeId(mutation.id()), WorkOperationGraphNodeType.MUTATION,
				false, null, null, null, null, null, null, null, null, null, List.of(), List.of(), mutation.id(),
				mutation.type(), mutation.effectiveBusinessDate(), mutation.occurredAt(), null, null, null);
	}

	private WorkOperationGraphNodeResponse stateNode(WorkOperationMutationGraphPort.StateNode state) {
		return new WorkOperationGraphNodeResponse(state.id(), WorkOperationGraphNodeType.STATE, false, null, null, null,
				null, null, null, null, null, null, List.of(state.orchidGroupId()), List.of(), null, null, null, null,
				state.orchidGroupId(), state.stateRevision(), state(state.state()));
	}

	private Map<Long, WorkOperation> relatedOperations(WorkOperation root) {
		Map<Long, WorkOperation> result = new LinkedHashMap<>();
		result.put(root.getId(), root);
		if (root.getParentOperation() != null) {
			operationRepository.findWithWorkTypeById(root.getParentOperation().getId())
				.ifPresent(operation -> result.putIfAbsent(operation.getId(), operation));
		}
		operationRepository
			.findByParentOperationIdAndRelationTypeOrderByIdAsc(root.getId(),
					WorkOperationRelationType.MOVEMENT_DISCARD)
			.forEach(operation -> result.putIfAbsent(operation.getId(), operation));
		return result;
	}

	private Map<Long, List<WorkOperationTarget>> targets(Collection<Long> operationIds) {
		return targetRepository.findByWorkOperationIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(operationIds)
			.stream()
			.collect(Collectors.groupingBy(target -> target.getWorkOperation().getId(), LinkedHashMap::new,
					Collectors.toList()));
	}

	private void addOrigin(WorkOperation root,
			com.greenhouse.backend.work.dto.operation.WorkOperationRelationSummaryResponse summary,
			List<WorkOperationGraphNodeResponse> nodes, List<WorkOperationGraphEdgeResponse> edges) {
		if (summary.originType() == WorkOperationOriginType.INBOUND) {
			for (Long inboundId : summary.inboundRecordIds()) {
				String id = "origin-inbound-" + inboundId;
				nodes.add(originNode(id, WorkOperationOriginType.INBOUND, inboundId));
				edges.add(new WorkOperationGraphEdgeResponse("originated-inbound-" + inboundId + "-" + root.getId(), id,
						operationNodeId(root.getId()), WorkOperationGraphEdgeType.ORIGINATED, null));
			}
			return;
		}
		String id = "origin-" + summary.originType().name().toLowerCase() + "-" + root.getId();
		nodes.add(originNode(id, summary.originType(), null));
		edges.add(new WorkOperationGraphEdgeResponse("originated-" + root.getId(), id, operationNodeId(root.getId()),
				WorkOperationGraphEdgeType.ORIGINATED, null));
	}

	private WorkOperationGraphNodeResponse originNode(String id, WorkOperationOriginType type, Long referenceId) {
		return new WorkOperationGraphNodeResponse(id, WorkOperationGraphNodeType.ORIGIN, false, type, referenceId, null,
				null, null, null, null, null, null, List.of(), List.of(), null, null, null, null, null, null, null);
	}

	private WorkOperationGraphNodeResponse operationNode(WorkOperation operation, boolean selected,
			List<WorkOperationTarget> targets) {
		List<Long> groupIds = targets.stream()
			.map(WorkOperationTarget::getOrchidGroupId)
			.filter(java.util.Objects::nonNull)
			.distinct()
			.toList();
		List<String> varieties = targets.stream().map(WorkOperationTarget::getVarietyNameSnapshot).distinct().toList();
		return new WorkOperationGraphNodeResponse(operationNodeId(operation.getId()),
				WorkOperationGraphNodeType.WORK_OPERATION, selected, null, null, null, operation.getId(),
				operation.getWorkType().getCode(), operation.getWorkType().getName(), operation.getTitle(),
				operation.getStatus().name(), operation.getPlannedStartDate(), groupIds, varieties, null, null, null,
				null, null, null, null);
	}

	private void addOperationRelations(Map<Long, WorkOperation> operations,
			List<WorkOperationGraphEdgeResponse> edges) {
		for (WorkOperation operation : operations.values()) {
			if (operation.getParentOperation() != null && operations.containsKey(operation.getParentOperation().getId())
					&& operation.getRelationType() == WorkOperationRelationType.MOVEMENT_DISCARD) {
				edges.add(new WorkOperationGraphEdgeResponse(
						"precedes-" + operation.getParentOperation().getId() + "-" + operation.getId(),
						operationNodeId(operation.getParentOperation().getId()), operationNodeId(operation.getId()),
						WorkOperationGraphEdgeType.PRECEDES, operation.getRelationType().name()));
			}
		}
	}

	private Map<Long, List<Long>> mutationIds(Collection<WorkOperation> operations) {
		Map<Long, Set<Long>> result = new LinkedHashMap<>();
		operations.forEach(operation -> result.put(operation.getId(), new LinkedHashSet<>()));
		effectRepository.findByWorkOperationIdInOrderByWorkOperationIdAscIdAsc(result.keySet()).forEach(effect -> {
			if (effect.getMutationId() != null) {
				result.get(effect.getWorkOperation().getId()).add(effect.getMutationId());
			}
		});
		correctionRepository.findByOriginalWorkOperationIdIn(result.keySet()).forEach(correction -> {
			if (correction.getMutationId() != null)
				result.get(correction.getOriginalWorkOperation().getId()).add(correction.getMutationId());
		});
		return result.entrySet()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue()), (left, right) -> left,
					LinkedHashMap::new));
	}

	private WorkOperationGraphStateResponse state(WorkOperationMutationGraphPort.State state) {
		if (state == null)
			return null;
		return new WorkOperationGraphStateResponse(state.quantity(), state.reservedQuantity(), state.status(),
				state.varietyId(), state.genus(), state.varietyName(), state.ageYear(), state.potSizeCode(),
				state.bedZoneId(), state.houseNumber(), state.physicalBedNumber(), state.bedZoneName(),
				state.startPosition(), state.endPosition());
	}

	private String operationNodeId(Long id) {
		return "work-operation-" + id;
	}

	private String mutationNodeId(Long id) {
		return "mutation-" + id;
	}

	private void validate(Long operationId, WorkOperationGraphDetail detail, int depth, int maxNodes) {
		if (operationId == null || operationId < 1)
			throw new IllegalArgumentException("작업 ID는 1 이상이어야 합니다.");
		if (detail == null)
			throw new IllegalArgumentException("그래프 상세 단계가 필요합니다.");
		if (depth < 0 || depth > MAX_DEPTH)
			throw new IllegalArgumentException("그래프 조회 깊이는 0~3이어야 합니다.");
		if (maxNodes < MIN_NODES || maxNodes > MAX_NODES)
			throw new IllegalArgumentException("그래프 노드 수는 10~300이어야 합니다.");
	}

}
