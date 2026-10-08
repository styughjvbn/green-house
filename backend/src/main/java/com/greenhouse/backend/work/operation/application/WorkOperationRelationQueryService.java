package com.greenhouse.backend.work.operation.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.operation.domain.WorkCommandReceipt;
import com.greenhouse.backend.work.operation.domain.WorkOperation;
import com.greenhouse.backend.work.operation.repository.WorkCommandReceiptMembershipRepository;
import com.greenhouse.backend.work.operation.repository.WorkCommandReceiptRepository;
import com.greenhouse.backend.work.operation.repository.WorkOperationRepository;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationRelationKind;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationSummaryResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkOperationRelationQueryService {

  private final WorkOperationRepository operationRepository;

  private final WorkCommandReceiptMembershipRepository membershipRepository;

  private final WorkCommandReceiptRepository receiptRepository;

  private final WorkOperationSummaryAssembler summaryAssembler;

  public List<WorkOperationSummaryResponse> get(Long operationId, WorkOperationRelationKind kind) {
    WorkOperation root =
        operationRepository
            .findWithWorkTypeById(operationId)
            .orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
    List<WorkOperation> operations =
        switch (kind) {
          case CREATION_BATCH -> creationBatch(root);
          case LINKED -> linkedOperations(root);
        };
    return summaryAssembler.assembleAll(operations);
  }

  private List<WorkOperation> creationBatch(WorkOperation root) {
    List<String> receiptKeys = membershipRepository.findReceiptKeysByOperationId(root.getId());
    Set<Long> operationIds = new LinkedHashSet<>();
    (receiptKeys.isEmpty()
            ? List.<WorkCommandReceipt>of()
            : receiptRepository.findByReceiptKeyIn(receiptKeys))
        .forEach(
            receipt -> {
              if (receipt.getResultOperationIds() != null) {
                operationIds.addAll(receipt.getResultOperationIds());
              }
            });
    operationIds.add(root.getId());
    return orderedOperations(operationIds);
  }

  private List<WorkOperation> linkedOperations(WorkOperation root) {
    Map<Long, WorkOperation> related = new LinkedHashMap<>();
    related.put(root.getId(), root);
    Set<Long> expanded = new LinkedHashSet<>();
    while (true) {
      List<Long> frontier = related.keySet().stream().filter(id -> !expanded.contains(id)).toList();
      if (frontier.isEmpty()) {
        break;
      }
      expanded.addAll(frontier);
      Set<Long> parentIds =
          frontier.stream()
              .map(related::get)
              .map(WorkOperation::getParentOperation)
              .filter(Objects::nonNull)
              .map(WorkOperation::getId)
              .filter(id -> !related.containsKey(id))
              .collect(Collectors.toCollection(LinkedHashSet::new));
      if (!parentIds.isEmpty()) {
        operationRepository
            .findWithWorkTypeByIdIn(parentIds.stream().toList())
            .forEach(operation -> related.putIfAbsent(operation.getId(), operation));
      }
      operationRepository
          .findByParentOperationIdInOrderByParentOperationIdAscIdAsc(frontier)
          .forEach(operation -> related.putIfAbsent(operation.getId(), operation));
    }

    Map<Long, Long> parentByChild = new LinkedHashMap<>();
    related
        .values()
        .forEach(
            operation -> {
              if (operation.getParentOperation() != null
                  && related.containsKey(operation.getParentOperation().getId())) {
                parentByChild.put(operation.getId(), operation.getParentOperation().getId());
              }
            });

    List<WorkOperation> ordered = new ArrayList<>();
    Set<Long> visited = new LinkedHashSet<>();
    related.values().stream()
        .filter(operation -> !parentByChild.containsKey(operation.getId()))
        .forEach(
            operation -> appendTree(operation, related.values(), parentByChild, visited, ordered));
    related
        .values()
        .forEach(
            operation -> appendTree(operation, related.values(), parentByChild, visited, ordered));
    return ordered;
  }

  private void appendTree(
      WorkOperation operation,
      Collection<WorkOperation> operations,
      Map<Long, Long> parentByChild,
      Set<Long> visited,
      List<WorkOperation> ordered) {
    if (!visited.add(operation.getId())) {
      return;
    }
    ordered.add(operation);
    operations.stream()
        .filter(candidate -> operation.getId().equals(parentByChild.get(candidate.getId())))
        .forEach(candidate -> appendTree(candidate, operations, parentByChild, visited, ordered));
  }

  private List<WorkOperation> orderedOperations(Collection<Long> operationIds) {
    Map<Long, WorkOperation> byId =
        operationRepository.findWithWorkTypeByIdIn(operationIds).stream()
            .collect(Collectors.toMap(WorkOperation::getId, Function.identity()));
    return operationIds.stream().map(byId::get).filter(Objects::nonNull).toList();
  }
}
