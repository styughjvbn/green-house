package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.api.operation.WorkOperationRelationType;
import com.greenhouse.backend.work.domain.operation.WorkCommandReceipt;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.dto.operation.WorkOperationOriginType;
import com.greenhouse.backend.work.dto.operation.WorkOperationRelationSummaryResponse;
import com.greenhouse.backend.work.repository.WorkCommandReceiptMembershipRepository;
import com.greenhouse.backend.work.repository.WorkCommandReceiptRepository;
import com.greenhouse.backend.work.repository.WorkOperationChildCount;
import com.greenhouse.backend.work.repository.WorkOperationInboundReference;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class WorkOperationRelationSummaryAssembler {

  private final WorkCommandReceiptRepository receiptRepository;

  private final WorkCommandReceiptMembershipRepository membershipRepository;

  private final WorkOperationRepository operationRepository;

  private final WorkOperationTargetRepository targetRepository;

  Map<Long, WorkOperationRelationSummaryResponse> assemble(Collection<WorkOperation> operations) {
    if (operations.isEmpty()) return Map.of();
    List<Long> ids = operations.stream().map(WorkOperation::getId).toList();
    Map<Long, List<WorkOperationInboundReference>> inboundReferences =
        targetRepository.findInboundReferences(ids).stream()
            .collect(
                Collectors.groupingBy(
                    WorkOperationInboundReference::operationId,
                    LinkedHashMap::new,
                    Collectors.toList()));
    Map<Long, Integer> batchSizes = new HashMap<>();
    List<String> receiptKeys =
        membershipRepository.findByOperationIdIn(ids).stream()
            .map(membership -> membership.getId().getReceiptKey())
            .distinct()
            .toList();
    for (WorkCommandReceipt receipt :
        receiptKeys.isEmpty()
            ? List.<WorkCommandReceipt>of()
            : receiptRepository.findByReceiptKeyIn(receiptKeys)) {
      if (receipt.getResultOperationIds() == null || receipt.getResultOperationIds().size() < 2)
        continue;
      for (Long operationId : receipt.getResultOperationIds())
        batchSizes.put(operationId, receipt.getResultOperationIds().size());
    }
    Map<Long, Integer> linkedCounts = linkedCounts(operations);

    Map<Long, WorkOperationRelationSummaryResponse> result = new LinkedHashMap<>();
    for (WorkOperation operation : operations) {
      List<Long> inboundIds =
          inboundReferences.getOrDefault(operation.getId(), List.of()).stream()
              .map(WorkOperationInboundReference::inboundRecordId)
              .toList();
      int linkedCount = linkedCounts.getOrDefault(operation.getId(), 0);
      result.put(
          operation.getId(),
          new WorkOperationRelationSummaryResponse(
              origin(operation, inboundIds),
              inboundIds,
              batchSizes.getOrDefault(operation.getId(), 1),
              linkedCount > 0,
              linkedCount));
    }
    return result;
  }

  Map<Long, WorkOperationRelationSummaryResponse> assembleOrigins(
      Collection<WorkOperation> operations, int maxReferences) {
    var ids = operations.stream().map(WorkOperation::getId).toList();
    var refs = targetRepository.findInboundReferences(ids, PageRequest.of(0, maxReferences));
    var result = new LinkedHashMap<Long, WorkOperationRelationSummaryResponse>();
    for (var operation : operations) {
      var inboundIds =
          refs.stream()
              .filter(ref -> ref.operationId().equals(operation.getId()))
              .map(WorkOperationInboundReference::inboundRecordId)
              .toList();
      result.put(
          operation.getId(),
          new WorkOperationRelationSummaryResponse(
              origin(operation, inboundIds), inboundIds, 1, false, 0));
    }
    return result;
  }

  private Map<Long, Integer> linkedCounts(Collection<WorkOperation> operations) {
    var rootIds = new LinkedHashSet<Long>();
    for (WorkOperation operation : operations) {
      Long rootId =
          operation.getParentOperation() == null
              ? operation.getId()
              : operation.getParentOperation().getId();
      rootIds.add(rootId);
    }
    var childCounts =
        operationRepository.countChildrenByParentIds(rootIds).stream()
            .collect(
                Collectors.toMap(
                    WorkOperationChildCount::parentOperationId,
                    WorkOperationChildCount::childCount));
    // Preserve the old last-group precedence when a page contains nested parent/child families.
    Map<Long, Long> rootsByOperationId = new HashMap<>();
    for (Long rootId : rootIds) {
      for (WorkOperation operation : operations) {
        if (operation.getId().equals(rootId)
            || operation.getParentOperation() != null
                && operation.getParentOperation().getId().equals(rootId)) {
          rootsByOperationId.put(operation.getId(), rootId);
        }
      }
    }

    Map<Long, Integer> result = new LinkedHashMap<>();
    operations.forEach(
        operation ->
            result.put(
                operation.getId(),
                Math.toIntExact(
                    childCounts.getOrDefault(rootsByOperationId.get(operation.getId()), 0L))));
    return result;
  }

  private WorkOperationOriginType origin(WorkOperation operation, List<Long> inboundIds) {
    if (!inboundIds.isEmpty()) return WorkOperationOriginType.INBOUND;
    if (operation.getRelationType() == WorkOperationRelationType.MOVEMENT_DISCARD) {
      return WorkOperationOriginType.SYSTEM;
    }
    return WorkOperationOriginType.WORK_MANAGEMENT;
  }
}
