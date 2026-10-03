package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.domain.operation.WorkCommandReceipt;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationRelationType;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import com.greenhouse.backend.work.dto.operation.WorkOperationOriginType;
import com.greenhouse.backend.work.dto.operation.WorkOperationRelationSummaryResponse;
import com.greenhouse.backend.work.repository.WorkCommandReceiptMembershipRepository;
import com.greenhouse.backend.work.repository.WorkCommandReceiptRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class WorkOperationRelationSummaryAssembler {

	private final WorkCommandReceiptRepository receiptRepository;

	private final WorkCommandReceiptMembershipRepository membershipRepository;

	private final WorkOperationRepository operationRepository;

	private final WorkOperationTargetRepository targetRepository;

	Map<Long, WorkOperationRelationSummaryResponse> assemble(Collection<WorkOperation> operations) {
		if (operations.isEmpty())
			return Map.of();
		List<Long> ids = operations.stream().map(WorkOperation::getId).toList();
		Map<Long, List<WorkOperationTarget>> targets = targetRepository
			.findByWorkOperationIdInOrderByWorkOperationIdAscIdAsc(ids)
			.stream()
			.collect(Collectors.groupingBy(target -> target.getWorkOperation().getId(), LinkedHashMap::new,
					Collectors.toList()));
		Map<Long, Integer> batchSizes = new HashMap<>();
		List<String> receiptKeys = membershipRepository.findByOperationIdIn(ids)
			.stream()
			.map(membership -> membership.getId().getReceiptKey())
			.distinct()
			.toList();
		for (WorkCommandReceipt receipt : receiptKeys.isEmpty() ? List.<WorkCommandReceipt>of()
				: receiptRepository.findByReceiptKeyIn(receiptKeys)) {
			if (receipt.getResultOperationIds() == null || receipt.getResultOperationIds().size() < 2)
				continue;
			for (Long operationId : receipt.getResultOperationIds())
				batchSizes.put(operationId, receipt.getResultOperationIds().size());
		}
		Map<Long, Integer> linkedCounts = linkedCounts(operations);

		Map<Long, WorkOperationRelationSummaryResponse> result = new LinkedHashMap<>();
		for (WorkOperation operation : operations) {
			List<Long> inboundIds = targets.getOrDefault(operation.getId(), List.of())
				.stream()
				.map(WorkOperationTarget::getInboundRecordId)
				.filter(Objects::nonNull)
				.distinct()
				.toList();
			int linkedCount = linkedCounts.getOrDefault(operation.getId(), 0);
			result.put(operation.getId(), new WorkOperationRelationSummaryResponse(origin(operation, inboundIds),
					inboundIds, batchSizes.getOrDefault(operation.getId(), 1), linkedCount > 0, linkedCount));
		}
		return result;
	}

	private Map<Long, Integer> linkedCounts(Collection<WorkOperation> operations) {
		Map<Long, Set<Long>> groupsByOperationId = new LinkedHashMap<>();
		Map<Long, Set<Long>> structuralGroups = new LinkedHashMap<>();
		for (WorkOperation operation : operations) {
			Long rootId = operation.getParentOperation() == null ? operation.getId()
					: operation.getParentOperation().getId();
			structuralGroups.computeIfAbsent(rootId, ignored -> new LinkedHashSet<>()).add(rootId);
			structuralGroups.get(rootId).add(operation.getId());
		}
		operationRepository
			.findByParentOperationIdInOrderByParentOperationIdAscIdAsc(structuralGroups.keySet().stream().toList())
			.forEach(operation -> structuralGroups
				.computeIfAbsent(operation.getParentOperation().getId(), ignored -> new LinkedHashSet<>())
				.add(operation.getId()));
		structuralGroups.values().forEach(group -> group.forEach(id -> groupsByOperationId.put(id, group)));

		Map<Long, Integer> result = new LinkedHashMap<>();
		operations.forEach(operation -> result.put(operation.getId(), Math.max(0,
				groupsByOperationId.getOrDefault(operation.getId(), Set.of(operation.getId())).size() - 1)));
		return result;
	}

	private WorkOperationOriginType origin(WorkOperation operation, List<Long> inboundIds) {
		if (!inboundIds.isEmpty())
			return WorkOperationOriginType.INBOUND;
		if (operation.getRelationType() == WorkOperationRelationType.MOVEMENT_DISCARD) {
			return WorkOperationOriginType.SYSTEM;
		}
		return WorkOperationOriginType.WORK_MANAGEMENT;
	}

}
