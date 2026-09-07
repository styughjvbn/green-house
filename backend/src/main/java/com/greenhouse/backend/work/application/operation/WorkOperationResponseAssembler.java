package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.application.target.InboundPottingPlanGateway;
import com.greenhouse.backend.work.application.target.InboundPottingPlanTarget;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import com.greenhouse.backend.work.domain.target.WorkTargetExecutionStatus;
import com.greenhouse.backend.work.domain.target.WorkTargetReferenceType;
import com.greenhouse.backend.work.dto.operation.WorkOperationProgressResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationResponse;
import com.greenhouse.backend.work.dto.target.WorkOperationTargetResponse;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class WorkOperationResponseAssembler {

	private static final Set<WorkOperationStatus> ACTIVE_STATUSES = Set.of(
			WorkOperationStatus.PLANNED,
			WorkOperationStatus.IN_PROGRESS,
			WorkOperationStatus.PAUSED);
	private static final Set<WorkTargetExecutionStatus> OPEN_TARGET_STATUSES = Set.of(
			WorkTargetExecutionStatus.PENDING,
			WorkTargetExecutionStatus.IN_PROGRESS,
			WorkTargetExecutionStatus.PARTIALLY_COMPLETED);

	private final WorkOperationTargetRepository targetRepository;
	private final WorkTargetExecutionRepository executionRepository;
	private final InboundPottingPlanGateway inboundPottingPlanGateway;
	private final WorkOperationActionResolver actionResolver;

	WorkOperationResponse assemble(WorkOperation operation) {
		return assembleAll(List.of(operation)).getFirst();
	}

	List<WorkOperationResponse> assembleAll(List<WorkOperation> operations) {
		if (operations.isEmpty()) {
			return List.of();
		}
		List<Long> operationIds = operations.stream().map(WorkOperation::getId).toList();
		List<WorkOperationTarget> targets = targetRepository
				.findByWorkOperationIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(operationIds);
		Map<Long, WorkTargetExecution> executionByTargetId = executionRepository
				.findByTargetWorkOperationIdInOrderByIdAsc(operationIds)
				.stream()
				.collect(Collectors.toMap(execution -> execution.getTarget().getId(), Function.identity()));
		Map<Long, InboundPottingPlanTarget> inboundById = currentInboundTargets(
				operations, targets, executionByTargetId);
		Map<Long, List<WorkOperationTarget>> targetsByOperationId = targets.stream()
				.collect(Collectors.groupingBy(
						target -> target.getWorkOperation().getId(),
						LinkedHashMap::new,
						Collectors.toList()));

		return operations.stream().map(operation -> {
			List<WorkOperationTargetResponse> targetResponses = targetsByOperationId
					.getOrDefault(operation.getId(), List.of())
					.stream()
					.map(target -> {
						WorkTargetExecution execution = executionByTargetId.get(target.getId());
						InboundPottingPlanTarget currentInbound = target.getInboundRecordId() == null
								? null
								: inboundById.get(target.getInboundRecordId());
						int currentQuantity = currentInbound == null
								? target.getQuantitySnapshot()
								: currentInbound.currentQuantity(target.getQuantitySnapshot());
						int remainingQuantity = Math.max(0, currentQuantity - execution.getProcessedQuantity());
						return WorkOperationTargetResponse.from(
								target,
								execution,
								currentInbound,
								actionResolver.resolveTarget(operation, execution, remainingQuantity));
					})
					.toList();
			WorkOperationProgressResponse progress = WorkOperationProgressResponse.from(targetResponses);
			return WorkOperationResponse.from(
					operation,
					targetResponses,
					actionResolver.resolveOperation(operation, progress));
		}).toList();
	}

	private Map<Long, InboundPottingPlanTarget> currentInboundTargets(
			List<WorkOperation> operations,
			List<WorkOperationTarget> targets,
			Map<Long, WorkTargetExecution> executionByTargetId) {
		Set<Long> activePottingOperationIds = operations.stream()
				.filter(operation -> WorkTypeDefinition.POTTING.name().equals(operation.getWorkType().getCode()))
				.filter(operation -> ACTIVE_STATUSES.contains(operation.getStatus()))
				.map(WorkOperation::getId)
				.collect(Collectors.toSet());
		List<Long> inboundRecordIds = targets.stream()
				.filter(target -> activePottingOperationIds.contains(target.getWorkOperation().getId()))
				.filter(target -> target.getTargetReferenceType() == WorkTargetReferenceType.INBOUND_RECORD)
				.filter(target -> {
					WorkTargetExecution execution = executionByTargetId.get(target.getId());
					return execution != null && OPEN_TARGET_STATUSES.contains(execution.getStatus());
				})
				.map(WorkOperationTarget::getInboundRecordId)
				.distinct()
				.toList();
		if (inboundRecordIds.isEmpty()) {
			return Map.of();
		}
		return inboundPottingPlanGateway.findCurrent(inboundRecordIds).stream()
				.collect(Collectors.toMap(InboundPottingPlanTarget::id, Function.identity()));
	}
}
