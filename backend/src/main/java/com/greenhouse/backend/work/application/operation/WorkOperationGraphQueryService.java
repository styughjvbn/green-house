package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.operation.WorkCommandReceipt;
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
import com.greenhouse.backend.work.repository.WorkCommandReceiptMembershipRepository;
import com.greenhouse.backend.work.repository.WorkCommandReceiptRepository;
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

	private final WorkCommandReceiptRepository receiptRepository;

	private final WorkCommandReceiptMembershipRepository membershipRepository;

	private final WorkOperationCorrectionRepository correctionRepository;

	private final WorkOperationRelationSummaryAssembler relationSummaryAssembler;

	private final WorkOperationMutationGraphPort mutationGraphPort;

	public WorkOperationGraphResponse get(Long operationId, WorkOperationGraphDetail detail, int depth, int maxNodes) {
		validate(operationId, detail, depth, maxNodes);
		WorkOperation root = operationRepository.findWithWorkTypeById(operationId)
			.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
		WorkCommandReceipt receipt = receipts(root.getId()).stream().findFirst().orElse(null);
		Map<Long, WorkOperation> operations = relatedOperations(root, receipt);
		Map<Long, List<WorkOperationTarget>> targets = targets(operations.keySet());
		var relationSummaries = relationSummaryAssembler.assemble(operations.values());
		List<WorkOperationGraphNodeResponse> nodes = new ArrayList<>();
		List<WorkOperationGraphEdgeResponse> edges = new ArrayList<>();

		addOrigin(root, relationSummaries.get(root.getId()), nodes, edges);
		addCreationBatch(root, receipt, operations.values(), nodes, edges);
		operations.values()
			.forEach(operation -> nodes.add(operationNode(operation, operationId.equals(operation.getId()),
					targets.getOrDefault(operation.getId(), List.of()))));
		addOperationRelations(operations, edges);

		boolean truncated = nodes.size() > maxNodes;
		if (truncated) {
			nodes.subList(maxNodes, nodes.size()).clear();
			Set<String> visible = nodes.stream().map(WorkOperationGraphNodeResponse::id).collect(Collectors.toSet());
			edges.removeIf(edge -> !visible.contains(edge.sourceNodeId()) || !visible.contains(edge.targetNodeId()));
		}

		if (detail != WorkOperationGraphDetail.WORK && nodes.size() < maxNodes) {
			Map<Long, List<Long>> mutationIdsByOperation = mutationIds(operations.values());
			List<Long> mutationIds = mutationIdsByOperation.values()
				.stream()
				.flatMap(Collection::stream)
				.distinct()
				.toList();
			int remaining = maxNodes - nodes.size();
			var fragment = mutationGraphPort.load(mutationIds, detail == WorkOperationGraphDetail.LINEAGE, depth,
					remaining);
			truncated |= fragment.truncated();
			fragment.mutations()
				.forEach(mutation -> nodes.add(new WorkOperationGraphNodeResponse(mutationNodeId(mutation.id()),
						WorkOperationGraphNodeType.MUTATION, false, null, null, null, null, null, null, null, null,
						null, List.of(), List.of(), mutation.id(), mutation.type(), mutation.effectiveBusinessDate(),
						mutation.occurredAt(), null, null, null)));
			fragment.states()
				.forEach(state -> nodes.add(new WorkOperationGraphNodeResponse(state.id(),
						WorkOperationGraphNodeType.STATE, false, null, null, null, null, null, null, null, null, null,
						List.of(state.orchidGroupId()), List.of(), null, null, null, null, state.orchidGroupId(),
						state.stateRevision(), state(state.state()))));
			fragment.edges()
				.forEach(edge -> edges.add(new WorkOperationGraphEdgeResponse(edge.id(), edge.sourceNodeId(),
						edge.targetNodeId(), WorkOperationGraphEdgeType.valueOf(edge.type()), edge.relationType())));
			Set<Long> visibleMutationIds = fragment.mutations()
				.stream()
				.map(WorkOperationMutationGraphPort.MutationNode::id)
				.collect(Collectors.toSet());
			for (var entry : mutationIdsByOperation.entrySet()) {
				for (Long mutationId : entry.getValue()) {
					if (visibleMutationIds.contains(mutationId)) {
						edges.add(new WorkOperationGraphEdgeResponse("effect-" + entry.getKey() + "-" + mutationId,
								operationNodeId(entry.getKey()), mutationNodeId(mutationId),
								WorkOperationGraphEdgeType.EFFECT, null));
					}
				}
			}
		}

		return new WorkOperationGraphResponse(operationId, detail, depth, maxNodes, truncated, List.copyOf(nodes),
				List.copyOf(edges));
	}

	private Map<Long, WorkOperation> relatedOperations(WorkOperation root, WorkCommandReceipt receipt) {
		Map<Long, WorkOperation> result = new LinkedHashMap<>();
		result.put(root.getId(), root);
		if (receipt != null && receipt.getResultOperationIds() != null && receipt.getResultOperationIds().size() > 1) {
			operationRepository.findWithWorkTypeByIdIn(receipt.getResultOperationIds())
				.forEach(operation -> result.putIfAbsent(operation.getId(), operation));
		}
		if (root.getParentOperation() != null) {
			operationRepository.findWithWorkTypeById(root.getParentOperation().getId())
				.ifPresent(operation -> result.putIfAbsent(operation.getId(), operation));
		}
		operationRepository
			.findByParentOperationIdAndRelationTypeOrderByIdAsc(root.getId(),
					WorkOperationRelationType.MOVEMENT_PRE_DISCARD)
			.forEach(operation -> result.putIfAbsent(operation.getId(), operation));
		correctionRepository
			.findByOriginalWorkOperationIdInOrCorrectionWorkOperationIdIn(List.of(root.getId()), List.of(root.getId()))
			.forEach(relation -> {
				result.putIfAbsent(relation.getOriginalWorkOperation().getId(), relation.getOriginalWorkOperation());
				result.putIfAbsent(relation.getCorrectionWorkOperation().getId(),
						relation.getCorrectionWorkOperation());
			});
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

	private void addCreationBatch(WorkOperation root, WorkCommandReceipt receipt, Collection<WorkOperation> operations,
			List<WorkOperationGraphNodeResponse> nodes, List<WorkOperationGraphEdgeResponse> edges) {
		if (receipt == null || receipt.getResultOperationIds() == null || receipt.getResultOperationIds().size() < 2)
			return;
		List<Long> siblingIds = receipt.getResultOperationIds();
		String batchId = "creation-batch-" + siblingIds.stream().min(Long::compareTo).orElse(root.getId());
		nodes.add(new WorkOperationGraphNodeResponse(batchId, WorkOperationGraphNodeType.CREATION_BATCH, false, null,
				null, siblingIds.size(), null, null, null, null, null, null, List.of(), List.of(), null, null, null,
				null, null, null, null));
		Set<Long> visibleIds = operations.stream().map(WorkOperation::getId).collect(Collectors.toSet());
		for (Long siblingId : siblingIds) {
			if (visibleIds.contains(siblingId))
				edges.add(new WorkOperationGraphEdgeResponse("same-command-" + batchId + "-" + siblingId, batchId,
						operationNodeId(siblingId), WorkOperationGraphEdgeType.SAME_COMMAND, null));
		}
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
					&& operation.getRelationType() == WorkOperationRelationType.MOVEMENT_PRE_DISCARD) {
				edges.add(new WorkOperationGraphEdgeResponse(
						"precedes-" + operation.getId() + "-" + operation.getParentOperation().getId(),
						operationNodeId(operation.getId()), operationNodeId(operation.getParentOperation().getId()),
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
		operations.forEach(operation -> {
			if (operation.getVoidMutationId() != null) {
				result.get(operation.getId()).add(operation.getVoidMutationId());
			}
		});
		return result.entrySet()
			.stream()
			.collect(Collectors.toMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue()), (left, right) -> left,
					LinkedHashMap::new));
	}

	private List<WorkCommandReceipt> receipts(Long operationId) {
		List<String> keys = membershipRepository.findReceiptKeysByOperationId(operationId);
		return keys.isEmpty() ? List.of() : receiptRepository.findByReceiptKeyIn(keys);
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
