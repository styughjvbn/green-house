package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.domain.operation.WorkCommandReceipt;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationRelationType;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import com.greenhouse.backend.work.dto.operation.WorkOperationOriginType;
import com.greenhouse.backend.work.dto.operation.WorkOperationRelationSummaryResponse;
import com.greenhouse.backend.work.repository.WorkCommandReceiptMembershipRepository;
import com.greenhouse.backend.work.repository.WorkCommandReceiptRepository;
import com.greenhouse.backend.work.repository.WorkOperationCorrectionRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

	private final WorkOperationCorrectionRepository correctionRepository;

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
		Set<Long> linked = new HashSet<>();
		operations.stream()
			.filter(operation -> operation.getParentOperation() != null)
			.forEach(operation -> linked.add(operation.getId()));
		operationRepository.findByParentOperationIdInOrderByParentOperationIdAscIdAsc(ids)
			.forEach(operation -> linked.add(operation.getParentOperation().getId()));
		correctionRepository.findByOriginalWorkOperationIdInOrCorrectionWorkOperationIdIn(ids, ids)
			.forEach(relation -> {
				linked.add(relation.getOriginalWorkOperation().getId());
				linked.add(relation.getCorrectionWorkOperation().getId());
			});

		Map<Long, WorkOperationRelationSummaryResponse> result = new LinkedHashMap<>();
		for (WorkOperation operation : operations) {
			List<Long> inboundIds = targets.getOrDefault(operation.getId(), List.of())
				.stream()
				.map(WorkOperationTarget::getInboundRecordId)
				.filter(java.util.Objects::nonNull)
				.distinct()
				.toList();
			result.put(operation.getId(), new WorkOperationRelationSummaryResponse(origin(operation, inboundIds),
					inboundIds, batchSizes.getOrDefault(operation.getId(), 1), linked.contains(operation.getId())));
		}
		return result;
	}

	private WorkOperationOriginType origin(WorkOperation operation, List<Long> inboundIds) {
		if (!inboundIds.isEmpty())
			return WorkOperationOriginType.INBOUND;
		if (operation.getRelationType() == WorkOperationRelationType.MOVEMENT_DISCARD
				|| WorkTypeDefinition.CORRECTION.name().equals(operation.getWorkType().getCode())) {
			return WorkOperationOriginType.SYSTEM;
		}
		return WorkOperationOriginType.WORK_MANAGEMENT;
	}

}
