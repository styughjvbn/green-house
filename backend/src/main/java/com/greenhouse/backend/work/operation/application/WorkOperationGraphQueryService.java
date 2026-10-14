package com.greenhouse.backend.work.operation.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.api.operation.WorkOperationRelationType;
import com.greenhouse.backend.work.correction.repository.WorkOperationCorrectionRepository;
import com.greenhouse.backend.work.effect.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.operation.domain.WorkOperation;
import com.greenhouse.backend.work.operation.repository.WorkOperationRepository;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationGraphDetail;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationGraphEdgeResponse;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationGraphEdgeType;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationGraphNodeResponse;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationGraphNodeType;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationGraphResponse;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationGraphStateResponse;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationOriginType;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationRelationSummaryResponse;
import com.greenhouse.backend.work.spi.operation.WorkOperationMutationGraphPort;
import com.greenhouse.backend.work.target.domain.WorkOperationTarget;
import com.greenhouse.backend.work.target.repository.WorkOperationTargetRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkOperationGraphQueryService {

  private static final int MAX_DEPTH = 3;

  private static final int MIN_NODES = 10;

  private static final int MAX_NODES = 300;
  private static final int MAX_REFERENCES = 1000;

  private final WorkOperationRepository operationRepository;

  private final WorkOperationTargetRepository targetRepository;

  private final WorkAppliedEffectRepository effectRepository;

  private final WorkOperationCorrectionRepository correctionRepository;

  private final WorkOperationRelationSummaryAssembler relationSummaryAssembler;

  private final WorkOperationMutationGraphPort mutationGraphPort;

  public WorkOperationGraphResponse get(
      Long operationId, WorkOperationGraphDetail detail, int depth, int maxNodes) {
    validate(operationId, detail, depth, maxNodes);
    WorkOperation root =
        operationRepository
            .findWithWorkTypeById(operationId)
            .orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
    var graph = new GraphAssembly(maxNodes);
    Map<Long, WorkOperation> seedOperations = relatedOperations(root, maxNodes, graph);
    Map<Long, List<Long>> seedMutationIds =
        detail == WorkOperationGraphDetail.WORK
            ? Map.of()
            : mutationIds(seedOperations.values(), graph);
    List<Long> rootMutationIds =
        seedMutationIds.values().stream().flatMap(Collection::stream).distinct().toList();
    WorkOperationMutationGraphPort.Fragment fragment =
        detail == WorkOperationGraphDetail.WORK
            ? WorkOperationMutationGraphPort.Fragment.empty()
            : mutationGraphPort.load(
                rootMutationIds, detail == WorkOperationGraphDetail.LINEAGE, depth, maxNodes);
    Map<Long, WorkOperation> operations = new LinkedHashMap<>(seedOperations);
    Map<Long, Set<Long>> operationIdsByMutation = operationIdsByMutation(seedMutationIds);
    if (detail == WorkOperationGraphDetail.LINEAGE) {
      addDiscoveredOperations(fragment, operations, operationIdsByMutation, graph);
    }
    Map<Long, List<WorkOperationTarget>> targets = targets(operations.keySet(), graph);
    var rootRelationSummary =
        relationSummaryAssembler.assembleOrigins(List.of(root), maxNodes + 1).get(root.getId());
    graph.includeTruncation(fragment.truncated());

    addOrigin(root, rootRelationSummary, graph);
    seedOperations
        .values()
        .forEach(
            operation ->
                graph.addSeedNode(
                    operationNode(
                        operation,
                        operationId.equals(operation.getId()),
                        targets.getOrDefault(operation.getId(), List.of()))));

    graph.trimSeedNodes();

    if (detail != WorkOperationGraphDetail.WORK && graph.hasRoom()) {
      addMutationFlow(
          new MutationFlowContext(operationId, operations, targets, operationIdsByMutation),
          fragment,
          graph);
    }
    Set<Long> visibleOperationIds = graph.visibleOperationIds();
    Map<Long, WorkOperation> visibleOperations =
        operations.entrySet().stream()
            .filter(entry -> visibleOperationIds.contains(entry.getKey()))
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (left, right) -> left,
                    LinkedHashMap::new));
    addOperationRelations(visibleOperations, graph);

    return graph.toResponse(operationId, detail, depth);
  }

  private void addDiscoveredOperations(
      WorkOperationMutationGraphPort.Fragment fragment,
      Map<Long, WorkOperation> operations,
      Map<Long, Set<Long>> operationIdsByMutation,
      GraphAssembly graph) {
    List<Long> mutationIds =
        fragment.mutations().stream().map(WorkOperationMutationGraphPort.MutationNode::id).toList();
    if (mutationIds.isEmpty()) {
      return;
    }
    graph
        .references(
            effectRepository.findByMutationIdInOrderByMutationIdAscIdAsc(
                mutationIds, PageRequest.of(0, MAX_REFERENCES + 1)))
        .forEach(
            effect -> {
              Long mutationId = effect.getMutationId();
              WorkOperation operation = effect.getWorkOperation();
              if (mutationId == null || operation == null) {
                return;
              }
              operations.putIfAbsent(operation.getId(), operation);
              operationIdsByMutation
                  .computeIfAbsent(mutationId, ignored -> new LinkedHashSet<>())
                  .add(operation.getId());
            });
    graph
        .references(
            correctionRepository.findByMutationIdIn(
                mutationIds, PageRequest.of(0, MAX_REFERENCES + 1)))
        .forEach(
            correction -> {
              var operation = correction.getOriginalWorkOperation();
              operations.putIfAbsent(operation.getId(), operation);
              operationIdsByMutation
                  .computeIfAbsent(correction.getMutationId(), ignored -> new LinkedHashSet<>())
                  .add(operation.getId());
            });
  }

  private Map<Long, Set<Long>> operationIdsByMutation(
      Map<Long, List<Long>> mutationIdsByOperation) {
    Map<Long, Set<Long>> result = new LinkedHashMap<>();
    mutationIdsByOperation.forEach(
        (operationId, mutationIds) ->
            mutationIds.forEach(
                mutationId ->
                    result
                        .computeIfAbsent(mutationId, ignored -> new LinkedHashSet<>())
                        .add(operationId)));
    return result;
  }

  private void addMutationFlow(
      MutationFlowContext context,
      WorkOperationMutationGraphPort.Fragment fragment,
      GraphAssembly graph) {
    Map<String, WorkOperationMutationGraphPort.StateNode> stateById =
        fragment.states().stream()
            .collect(
                Collectors.toMap(WorkOperationMutationGraphPort.StateNode::id, value -> value));
    Map<String, List<WorkOperationMutationGraphPort.Edge>> stateEdgesByMutation =
        fragment.edges().stream()
            .filter(
                edge ->
                    edge.type().equals(WorkOperationGraphEdgeType.STATE_INPUT.name())
                        || edge.type().equals(WorkOperationGraphEdgeType.STATE_OUTPUT.name()))
            .collect(
                Collectors.groupingBy(
                    edge ->
                        edge.type().equals(WorkOperationGraphEdgeType.STATE_INPUT.name())
                            ? edge.targetNodeId()
                            : edge.sourceNodeId(),
                    LinkedHashMap::new,
                    Collectors.toList()));
    Set<Long> visibleMutationIds = new LinkedHashSet<>();
    graph.includeTruncation(fragment.truncated());

    for (var mutation : fragment.mutations()) {
      String mutationNodeId = mutationNodeId(mutation.id());
      Set<String> stateIds =
          stateEdgesByMutation.getOrDefault(mutationNodeId, List.of()).stream()
              .flatMap(edge -> List.of(edge.sourceNodeId(), edge.targetNodeId()).stream())
              .filter(stateById::containsKey)
              .collect(Collectors.toCollection(LinkedHashSet::new));
      Set<Long> newOperationIds =
          context.operationIdsByMutation().getOrDefault(mutation.id(), Set.of()).stream()
              .filter(id -> !graph.containsNode(operationNodeId(id)))
              .collect(Collectors.toCollection(LinkedHashSet::new));
      long newStateCount = stateIds.stream().filter(id -> !graph.containsNode(id)).count();
      int candidateCount = 1 + newOperationIds.size() + Math.toIntExact(newStateCount);
      if (!graph.canFitMutationFlow(candidateCount)) {
        continue;
      }
      for (Long operationId : newOperationIds) {
        WorkOperation operation = context.operations().get(operationId);
        if (operation == null) {
          continue;
        }
        graph.addNode(
            operationNode(
                operation,
                context.selectedOperationId().equals(operationId),
                context.targets().getOrDefault(operationId, List.of())));
      }
      graph.addNode(mutationNode(mutation));
      visibleMutationIds.add(mutation.id());
      for (String stateId : stateIds) {
        if (!graph.containsNode(stateId)) {
          graph.addNode(stateNode(stateById.get(stateId)));
        }
      }
    }

    fragment.edges().stream()
        .filter(
            edge ->
                graph.containsNode(edge.sourceNodeId()) && graph.containsNode(edge.targetNodeId()))
        .forEach(
            edge ->
                graph.addEdge(
                    new WorkOperationGraphEdgeResponse(
                        edge.id(),
                        edge.sourceNodeId(),
                        edge.targetNodeId(),
                        WorkOperationGraphEdgeType.valueOf(edge.type()),
                        edge.relationType())));
    visibleMutationIds.forEach(
        mutationId ->
            context.operationIdsByMutation().getOrDefault(mutationId, Set.of()).stream()
                .filter(operationId -> graph.containsNode(operationNodeId(operationId)))
                .forEach(
                    operationId ->
                        graph.addEdge(
                            new WorkOperationGraphEdgeResponse(
                                "effect-" + operationId + "-" + mutationId,
                                operationNodeId(operationId),
                                mutationNodeId(mutationId),
                                WorkOperationGraphEdgeType.EFFECT,
                                null))));
  }

  private WorkOperationGraphNodeResponse mutationNode(
      WorkOperationMutationGraphPort.MutationNode mutation) {
    return WorkOperationGraphNodeResponse.mutation(
        mutationNodeId(mutation.id()),
        mutation.id(),
        mutation.type(),
        mutation.effectiveBusinessDate(),
        mutation.occurredAt());
  }

  private WorkOperationGraphNodeResponse stateNode(WorkOperationMutationGraphPort.StateNode state) {
    return WorkOperationGraphNodeResponse.state(
        state.id(), state.orchidGroupId(), state.stateRevision(), state(state.state()));
  }

  private Map<Long, WorkOperation> relatedOperations(
      WorkOperation root, int maxNodes, GraphAssembly graph) {
    Map<Long, WorkOperation> result = new LinkedHashMap<>();
    result.put(root.getId(), root);
    if (root.getParentOperation() != null) {
      operationRepository
          .findWithWorkTypeById(root.getParentOperation().getId())
          .ifPresent(operation -> result.putIfAbsent(operation.getId(), operation));
    }
    var children =
        operationRepository.findByParentOperationIdAndRelationTypeOrderByIdAsc(
            root.getId(),
            WorkOperationRelationType.MOVEMENT_DISCARD,
            PageRequest.of(0, maxNodes + 1));
    if (children.size() > maxNodes) graph.includeTruncation(true);
    children.stream()
        .limit(maxNodes)
        .forEach(operation -> result.putIfAbsent(operation.getId(), operation));
    return result;
  }

  private Map<Long, List<WorkOperationTarget>> targets(
      Collection<Long> operationIds, GraphAssembly graph) {
    return graph
        .references(
            targetRepository
                .findByWorkOperationIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(
                    operationIds, PageRequest.of(0, MAX_REFERENCES + 1)))
        .stream()
        .collect(
            Collectors.groupingBy(
                target -> target.getWorkOperation().getId(),
                LinkedHashMap::new,
                Collectors.toList()));
  }

  private void addOrigin(
      WorkOperation root, WorkOperationRelationSummaryResponse summary, GraphAssembly graph) {
    if (summary.originType() == WorkOperationOriginType.INBOUND) {
      for (Long inboundId : summary.inboundRecordIds()) {
        String id = "origin-inbound-" + inboundId;
        graph.addSeedNode(
            WorkOperationGraphNodeResponse.origin(id, WorkOperationOriginType.INBOUND, inboundId));
        graph.addEdge(
            new WorkOperationGraphEdgeResponse(
                "originated-inbound-" + inboundId + "-" + root.getId(),
                id,
                operationNodeId(root.getId()),
                WorkOperationGraphEdgeType.ORIGINATED,
                null));
      }
      return;
    }
    String id = "origin-" + summary.originType().name().toLowerCase() + "-" + root.getId();
    graph.addSeedNode(WorkOperationGraphNodeResponse.origin(id, summary.originType(), null));
    graph.addEdge(
        new WorkOperationGraphEdgeResponse(
            "originated-" + root.getId(),
            id,
            operationNodeId(root.getId()),
            WorkOperationGraphEdgeType.ORIGINATED,
            null));
  }

  private WorkOperationGraphNodeResponse operationNode(
      WorkOperation operation, boolean selected, List<WorkOperationTarget> targets) {
    List<Long> groupIds =
        targets.stream()
            .map(WorkOperationTarget::getOrchidGroupId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    List<String> varieties =
        targets.stream().map(WorkOperationTarget::getVarietyNameSnapshot).distinct().toList();
    return WorkOperationGraphNodeResponse.operation(
        operationNodeId(operation.getId()),
        selected,
        operation.getId(),
        operation.getWorkType().getCode(),
        operation.getWorkType().getName(),
        operation.getTitle(),
        operation.getStatus().name(),
        operation.getPlannedStartDate(),
        groupIds,
        varieties);
  }

  private void addOperationRelations(Map<Long, WorkOperation> operations, GraphAssembly graph) {
    for (WorkOperation operation : operations.values()) {
      if (operation.getParentOperation() != null
          && operations.containsKey(operation.getParentOperation().getId())
          && operation.getRelationType() == WorkOperationRelationType.MOVEMENT_DISCARD) {
        graph.addEdge(
            new WorkOperationGraphEdgeResponse(
                "precedes-" + operation.getParentOperation().getId() + "-" + operation.getId(),
                operationNodeId(operation.getParentOperation().getId()),
                operationNodeId(operation.getId()),
                WorkOperationGraphEdgeType.PRECEDES,
                operation.getRelationType().name()));
      }
    }
  }

  private Map<Long, List<Long>> mutationIds(
      Collection<WorkOperation> operations, GraphAssembly graph) {
    Map<Long, Set<Long>> result = new LinkedHashMap<>();
    operations.forEach(operation -> result.put(operation.getId(), new LinkedHashSet<>()));
    graph
        .references(
            effectRepository.findByWorkOperationIdInOrderByWorkOperationIdAscIdAsc(
                result.keySet(), PageRequest.of(0, MAX_REFERENCES + 1)))
        .forEach(
            effect -> {
              if (effect.getMutationId() != null) {
                result.get(effect.getWorkOperation().getId()).add(effect.getMutationId());
              }
            });
    graph
        .references(
            correctionRepository.findByOriginalWorkOperationIdIn(
                result.keySet(), PageRequest.of(0, MAX_REFERENCES + 1)))
        .forEach(
            correction -> {
              if (correction.getMutationId() != null)
                result
                    .get(correction.getOriginalWorkOperation().getId())
                    .add(correction.getMutationId());
            });
    return result.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue()),
                (left, right) -> left,
                LinkedHashMap::new));
  }

  private WorkOperationGraphStateResponse state(WorkOperationMutationGraphPort.State state) {
    if (state == null) return null;
    return new WorkOperationGraphStateResponse(
        state.quantity(),
        state.reservedQuantity(),
        state.status(),
        state.varietyId(),
        state.genus(),
        state.varietyName(),
        state.ageYear(),
        state.potSizeCode(),
        state.bedZoneId(),
        state.houseNumber(),
        state.physicalBedNumber(),
        state.bedZoneName(),
        state.startPosition(),
        state.endPosition());
  }

  private String operationNodeId(Long id) {
    return "work-operation-" + id;
  }

  private String mutationNodeId(Long id) {
    return "mutation-" + id;
  }

  private record MutationFlowContext(
      Long selectedOperationId,
      Map<Long, WorkOperation> operations,
      Map<Long, List<WorkOperationTarget>> targets,
      Map<Long, Set<Long>> operationIdsByMutation) {}

  private static final class GraphAssembly {

    private final int maxNodes;
    private final List<WorkOperationGraphNodeResponse> nodes = new ArrayList<>();
    private final List<WorkOperationGraphEdgeResponse> edges = new ArrayList<>();
    private final Set<String> visibleNodeIds = new LinkedHashSet<>();
    private boolean truncated;

    private GraphAssembly(int maxNodes) {
      this.maxNodes = maxNodes;
    }

    private void addSeedNode(WorkOperationGraphNodeResponse node) {
      nodes.add(node);
    }

    private void addNode(WorkOperationGraphNodeResponse node) {
      nodes.add(node);
      visibleNodeIds.add(node.id());
    }

    private void addEdge(WorkOperationGraphEdgeResponse edge) {
      edges.add(edge);
    }

    private boolean containsNode(String id) {
      return visibleNodeIds.contains(id);
    }

    private boolean hasRoom() {
      return nodes.size() < maxNodes;
    }

    private void trimSeedNodes() {
      if (nodes.size() > maxNodes) {
        truncated = true;
        nodes.subList(maxNodes, nodes.size()).clear();
      }
      visibleNodeIds.clear();
      nodes.forEach(node -> visibleNodeIds.add(node.id()));
      if (truncated) {
        edges.removeIf(
            edge -> !containsNode(edge.sourceNodeId()) || !containsNode(edge.targetNodeId()));
      }
    }

    private boolean canFitMutationFlow(int candidateCount) {
      if (nodes.size() + candidateCount <= maxNodes) return true;
      truncated = true;
      return false;
    }

    private void includeTruncation(boolean fragmentTruncated) {
      truncated |= fragmentTruncated;
    }

    private <T> List<T> references(List<T> references) {
      if (references.size() <= MAX_REFERENCES) return references;
      truncated = true;
      return references.subList(0, MAX_REFERENCES);
    }

    private Set<Long> visibleOperationIds() {
      return nodes.stream()
          .filter(node -> node.nodeType() == WorkOperationGraphNodeType.WORK_OPERATION)
          .map(WorkOperationGraphNodeResponse::workOperationId)
          .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private WorkOperationGraphResponse toResponse(
        Long operationId, WorkOperationGraphDetail detail, int depth) {
      return new WorkOperationGraphResponse(
          operationId, detail, depth, maxNodes, truncated, List.copyOf(nodes), List.copyOf(edges));
    }
  }

  private void validate(
      Long operationId, WorkOperationGraphDetail detail, int depth, int maxNodes) {
    if (operationId == null || operationId < 1)
      throw new IllegalArgumentException("작업 ID는 1 이상이어야 합니다.");
    if (detail == null) throw new IllegalArgumentException("그래프 상세 단계가 필요합니다.");
    if (depth < 0 || depth > MAX_DEPTH)
      throw new IllegalArgumentException("그래프 조회 깊이는 0~3이어야 합니다.");
    if (maxNodes < MIN_NODES || maxNodes > MAX_NODES)
      throw new IllegalArgumentException("그래프 노드 수는 10~300이어야 합니다.");
  }
}
