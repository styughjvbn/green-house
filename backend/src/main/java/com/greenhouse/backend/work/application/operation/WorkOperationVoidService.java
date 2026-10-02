package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.application.target.InboundPottingPlanGateway;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationRelationType;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import com.greenhouse.backend.work.domain.operation.WorkTypeWorkflow;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import com.greenhouse.backend.work.domain.target.WorkTargetExecutionStatus;
import com.greenhouse.backend.work.domain.target.WorkTargetReferenceType;
import com.greenhouse.backend.work.dto.operation.WorkOperationBatchCancellationRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationBatchCancellationResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationCancellationEligibilityResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationCancellationRequest;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkOperationVoidService {

	private final WorkOperationRepository operationRepository;

	private final WorkAppliedEffectRepository effectRepository;

	private final WorkTargetExecutionRepository executionRepository;

	private final WorkOperationTargetRepository targetRepository;

	private final StructureChangeVoidPort structureChangeVoidPort;

	private final PottingVoidPort pottingVoidPort;

	private final InboundPottingPlanGateway inboundPottingPlanGateway;

	private final WorkOperationQueryService queryService;

	private final WorkOperationSupport support;

	@Transactional(readOnly = true)
	public WorkOperationCancellationEligibilityResponse eligibility(Long operationId) {
		var operation = operationRepository.findWithWorkTypeById(operationId)
			.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
		return inspectCancellation(operationId, operation).toResponse();
	}

	private CancellationInspection inspectCancellation(Long operationId, WorkOperation operation) {
		var blockers = new ArrayList<WorkOperationCancellationEligibilityResponse.Blocker>();
		if (operation.getStatus() == WorkOperationStatus.STOPPED
				|| operation.getStatus() == WorkOperationStatus.CANCELED
				|| operation.getStatus() == WorkOperationStatus.VOIDED) {
			blockers.add(new WorkOperationCancellationEligibilityResponse.Blocker("ALREADY_CLOSED",
					"이미 종료되었거나 취소된 작업입니다.", 1));
		}
		else if (operation.getRelationType() == WorkOperationRelationType.MOVEMENT_DISCARD) {
			blockers.add(new WorkOperationCancellationEligibilityResponse.Blocker("VOID_WITH_PARENT_MOVEMENT",
					"이 폐기는 연관된 자리 이동 작업에서 함께 취소해야 합니다.", 1));
		}
		else if (!cancelableStatus(operation.getStatus()) || !operation.getWorkType().supportsUserCancellation()) {
			blockers.add(new WorkOperationCancellationEligibilityResponse.Blocker("UNSUPPORTED_OPERATION",
					"이 상태와 작업 유형은 취소할 수 없습니다.", 1));
		}
		if (!blockers.isEmpty()) {
			return new CancellationInspection(operation, List.of(), List.of(), List.of(), List.copyOf(blockers));
		}
		var relatedDiscards = operation.getRelationType() == null ? operationRepository
			.findByParentOperationIdAndRelationTypeOrderByIdAsc(operationId, WorkOperationRelationType.MOVEMENT_DISCARD)
				: List.<WorkOperation>of();
		relatedDiscards.forEach(discard -> {
			if (discard.getStatus() != WorkOperationStatus.COMPLETED) {
				blockers.add(new WorkOperationCancellationEligibilityResponse.Blocker("RELATED_DISCARD_NOT_COMPLETED",
						"연관된 이동 후 잔여 난 폐기 작업의 상태가 완료가 아닙니다.", 1));
			}
		});
		List<Long> targetOperationIds = new ArrayList<>();
		targetOperationIds.add(operationId);
		targetOperationIds.addAll(relatedDiscards.stream().map(WorkOperation::getId).toList());
		var effects = targetOperationIds.stream()
			.flatMap(id -> effectRepository.findByWorkOperationIdOrderByIdAsc(id).stream())
			.toList();
		List<Long> mutationIds = effects.stream()
			.map(effect -> effect.getMutationId())
			.filter(Objects::nonNull)
			.distinct()
			.sorted()
			.toList();
		if (mutationIds.isEmpty()) {
			var groups = new LinkedHashMap<Long, WorkOperationCancellationEligibilityResponse.AffectedOrchidGroup>();
			targetRepository
				.findByWorkOperationIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(targetOperationIds)
				.stream()
				.filter(target -> target.getTargetReferenceType() == WorkTargetReferenceType.ORCHID_GROUP)
				.forEach(target -> groups.putIfAbsent(target.getOrchidGroupId(),
						new WorkOperationCancellationEligibilityResponse.AffectedOrchidGroup(target.getOrchidGroupId(),
								target.getVarietyNameSnapshot(), target.getQuantitySnapshot(),
								WorkOperationCancellationEligibilityResponse.ImpactType.RECORD_CANCELED)));
			return new CancellationInspection(operation, relatedDiscards, mutationIds, List.copyOf(groups.values()),
					List.copyOf(blockers));
		}
		boolean potting = operation.getWorkType().workflow() == WorkTypeWorkflow.POTTING;
		List<StructureChangeVoidPort.OrchidGroupSummary> sourceGroups;
		List<StructureChangeVoidPort.OrchidGroupSummary> resultGroups;
		List<StructureChangeVoidPort.Blocker> inspectionBlockers;
		if (potting) {
			var inspection = pottingVoidPort.inspect(operationId,
					effects.stream()
						.map(effect -> new PottingVoidPort.Effect(
								effect.getTarget() == null ? null : effect.getTarget().getInboundRecordId(),
								effect.getMutationId()))
						.toList());
			sourceGroups = List.of();
			resultGroups = inspection.resultOrchidGroups();
			inspectionBlockers = inspection.blockers();
		}
		else {
			var inspection = structureChangeVoidPort.inspect(operationId, mutationIds);
			sourceGroups = inspection.sourceOrchidGroups();
			resultGroups = inspection.resultOrchidGroups();
			inspectionBlockers = inspection.blockers();
		}
		inspectionBlockers
			.forEach(blocker -> blockers.add(new WorkOperationCancellationEligibilityResponse.Blocker(blocker.code(),
					blocker.message(), blocker.count())));
		List<WorkOperationCancellationEligibilityResponse.AffectedOrchidGroup> affectedGroups = new ArrayList<>();
		sourceGroups.forEach(group -> affectedGroups
			.add(toAffectedGroup(group, WorkOperationCancellationEligibilityResponse.ImpactType.RESTORED)));
		resultGroups.forEach(group -> affectedGroups
			.add(toAffectedGroup(group, WorkOperationCancellationEligibilityResponse.ImpactType.CREATION_CANCELED)));
		return new CancellationInspection(operation, relatedDiscards, mutationIds, List.copyOf(affectedGroups),
				List.copyOf(blockers));
	}

	private WorkOperationCancellationEligibilityResponse.AffectedOrchidGroup toAffectedGroup(
			StructureChangeVoidPort.OrchidGroupSummary group,
			WorkOperationCancellationEligibilityResponse.ImpactType impactType) {
		return new WorkOperationCancellationEligibilityResponse.AffectedOrchidGroup(group.orchidGroupId(),
				group.varietyName(), group.quantity(), impactType);
	}

	public WorkOperationView voidOperation(Long operationId, WorkOperationCancellationRequest request) {
		return cancelOperation(operationId, request);
	}

	public WorkOperationBatchCancellationResponse cancelBatch(WorkOperationBatchCancellationRequest request) {
		List<Long> ids = request.workOperationIds().stream().distinct().sorted().toList();
		if (ids.isEmpty() || ids.size() > 100)
			throw new IllegalArgumentException("일괄 취소 작업은 1~100건이어야 합니다.");
		String key = support.normalizeRequired(request.idempotencyKey());
		String reason = support.normalizeRequired(request.reason());
		List<WorkOperation> operations = operationRepository.findAllForUpdateByIdIn(ids);
		if (operations.size() != ids.size()) {
			throw new NotFoundException("일괄 취소할 작업을 찾을 수 없습니다.");
		}
		operationRepository.findByIdIn(ids);
		boolean replayOnly = operations.stream()
			.allMatch(operation -> operation.getStatus() == WorkOperationStatus.VOIDED
					&& batchRequestKey(key, operation.getId()).equals(operation.getVoidRequestKey()));
		if (!replayOnly
				&& operations.stream().anyMatch(operation -> operation.getStatus() != WorkOperationStatus.COMPLETED)) {
			throw new IllegalArgumentException("일괄 취소는 완료된 구조 변경·이동·폐기 작업만 지원합니다.");
		}
		Set<Long> operationIds = Set.copyOf(ids);
		for (var operation : operations) {
			var workflow = operation.getWorkType().workflow();
			if (!operation.getWorkType().supportsMutationVoid() || workflow == WorkTypeWorkflow.POTTING) {
				throw new IllegalArgumentException("일괄 취소는 구조 변경·이동·폐기 작업만 지원합니다.");
			}
			if (operation.getRelationType() == WorkOperationRelationType.MOVEMENT_DISCARD
					&& (operation.getParentOperation() == null
							|| !operationIds.contains(operation.getParentOperation().getId()))) {
				throw new IllegalArgumentException("연관 폐기는 원본 자리 이동 작업과 함께 선택해야 합니다.");
			}
		}
		if (operationRepository.findByParentOperationIdInOrderByParentOperationIdAscIdAsc(ids)
			.stream()
			.filter(item -> item.getRelationType() == WorkOperationRelationType.MOVEMENT_DISCARD)
			.anyMatch(item -> !operationIds.contains(item.getId()))) {
			throw new IllegalArgumentException("자리 이동의 연관 폐기 작업도 함께 선택해야 합니다.");
		}
		var effects = effectRepository.findByWorkOperationIdInOrderByWorkOperationIdAscIdAsc(ids);
		if (effects.stream().anyMatch(effect -> effect.getMutationId() == null) || !effects.stream()
			.map(effect -> effect.getWorkOperation().getId())
			.collect(java.util.stream.Collectors.toSet())
			.containsAll(operationIds)) {
			throw new IllegalArgumentException("모든 선택 작업에 취소할 Mutation이 있어야 합니다.");
		}
		var mutationIds = effects.stream().map(effect -> effect.getMutationId()).distinct().sorted().toList();
		Long compensationId = structureChangeVoidPort.compensateBatch(operationIds, key, mutationIds,
				request.creationCancellationOrchidGroupIds(), operations.getFirst().getPlannedStartDate(), reason,
				replayOnly);
		if (!replayOnly) {
			var now = support.now();
			effects.forEach(effect -> effect.cancel(now));
			cancelOpenExecutions(executionRepository.findByTargetWorkOperationIdInOrderByIdAsc(ids), now);
			for (var operation : operations) {
				operation.voidCompletedMutationWork(now, reason, batchRequestKey(key, operation.getId()),
						compensationId);
			}
		}
		return new WorkOperationBatchCancellationResponse(ids, compensationId,
				request.creationCancellationOrchidGroupIds());
	}

	public WorkOperationView cancelOperation(Long operationId, WorkOperationCancellationRequest request) {
		var operation = operationRepository.findForUpdateById(operationId)
			.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
		String requestKey = support.normalizeRequired(request.idempotencyKey());
		if (operation.getStatus() == WorkOperationStatus.CANCELED
				|| operation.getStatus() == WorkOperationStatus.VOIDED) {
			if (!requestKey.equals(operation.getVoidRequestKey())) {
				throw new IllegalArgumentException("이미 다른 요청으로 취소된 작업입니다.");
			}
			return queryService.get(operationId);
		}
		var inspection = inspectCancellation(operationId, operation);
		if (!inspection.cancellable()) {
			throw new IllegalArgumentException(inspection.blockers().getFirst().message());
		}
		String reason = support.normalizeRequired(request.reason());
		var now = support.now();
		var executions = executionRepository.findByTargetWorkOperationIdOrderByIdAsc(operationId);
		if (inspection.mutationIds().isEmpty()) {
			effectRepository.findByWorkOperationIdOrderByIdAsc(operationId).forEach(effect -> effect.cancel(now));
			cancelOpenExecutions(executions, now);
			closeInboundPottingPlans(operation, executions);
			operation.cancelRecordedWork(now, reason, requestKey);
			return queryService.get(operationId);
		}
		Long mutationId;
		if (operation.getWorkType().workflow() == WorkTypeWorkflow.POTTING) {
			var effects = effectRepository.findByWorkOperationIdOrderByIdAsc(operationId);
			mutationId = pottingVoidPort.compensate(operationId, requestKey,
					effects.stream()
						.map(effect -> new PottingVoidPort.Effect(
								effect.getTarget() == null ? null : effect.getTarget().getInboundRecordId(),
								effect.getMutationId()))
						.toList(),
					operation.getPlannedStartDate(), reason, true);
		}
		else {
			mutationId = structureChangeVoidPort.compensate(operationId, requestKey, inspection.mutationIds(),
					operation.getPlannedStartDate(), reason);
		}
		cancelOpenExecutions(executions, now);
		closeInboundPottingPlans(operation, executions);
		List<WorkOperation> relatedDiscards = operationRepository.findByParentOperationIdAndRelationTypeOrderByIdAsc(
				operationId, WorkOperationRelationType.MOVEMENT_DISCARD);
		for (int index = relatedDiscards.size() - 1; index >= 0; index--) {
			var discard = relatedDiscards.get(index);
			effectRepository.findByWorkOperationIdOrderByIdAsc(discard.getId()).forEach(effect -> effect.cancel(now));
			discard.voidCompletedMutationWork(now, reason, relatedRequestKey(requestKey, discard.getId()), mutationId);
		}
		effectRepository.findByWorkOperationIdOrderByIdAsc(operationId).forEach(effect -> effect.cancel(now));
		operation.voidCompletedMutationWork(now, reason, requestKey, mutationId);
		return queryService.get(operationId);
	}

	private boolean cancelableStatus(WorkOperationStatus status) {
		return status == WorkOperationStatus.PLANNED || status == WorkOperationStatus.IN_PROGRESS
				|| status == WorkOperationStatus.PAUSED || status == WorkOperationStatus.COMPLETED;
	}

	private void cancelOpenExecutions(List<WorkTargetExecution> executions, java.time.LocalDateTime canceledAt) {
		executions.stream()
			.filter(execution -> execution.getStatus() != WorkTargetExecutionStatus.COMPLETED)
			.filter(execution -> execution.getStatus() != WorkTargetExecutionStatus.SKIPPED)
			.forEach(execution -> execution.cancel(canceledAt));
	}

	private void closeInboundPottingPlans(WorkOperation operation, List<WorkTargetExecution> executions) {
		if (operation.getWorkType().workflow() != WorkTypeWorkflow.POTTING) {
			return;
		}
		List<Long> inboundRecordIds = executions.stream()
			.filter(execution -> execution.getStatus() != WorkTargetExecutionStatus.COMPLETED)
			.map(WorkTargetExecution::getTarget)
			.filter(target -> target.getTargetReferenceType() == WorkTargetReferenceType.INBOUND_RECORD)
			.map(target -> target.getInboundRecordId())
			.distinct()
			.toList();
		if (!inboundRecordIds.isEmpty()) {
			inboundPottingPlanGateway.closePottingPlan(inboundRecordIds);
		}
	}

	public void voidInboundRegistration(Long operationId, WorkOperationCancellationRequest request) {
		var operation = operationRepository.findWithWorkTypeById(operationId)
			.orElseThrow(() -> new NotFoundException("입고 작업을 찾을 수 없습니다."));
		String requestKey = support.normalizeRequired(request.idempotencyKey());
		if (operation.getStatus() == WorkOperationStatus.VOIDED) {
			if (!requestKey.equals(operation.getVoidRequestKey())) {
				throw new IllegalArgumentException("이미 다른 요청으로 무효화된 입고 작업입니다.");
			}
			return;
		}
		if (operation.getStatus() != WorkOperationStatus.COMPLETED
				|| !WorkTypeDefinition.INBOUND.name().equals(operation.getWorkType().getCode())) {
			throw new IllegalArgumentException("완료된 즉시 배치 입고 작업만 취소할 수 있습니다.");
		}
		String reason = support.normalizeRequired(request.reason());
		var effects = effectRepository.findByWorkOperationIdOrderByIdAsc(operationId);
		var portEffects = effects.stream()
			.map(effect -> new PottingVoidPort.Effect(
					effect.getTarget() == null ? null : effect.getTarget().getInboundRecordId(),
					effect.getMutationId()))
			.toList();
		var inspection = pottingVoidPort.inspect(operationId, portEffects);
		if (!inspection.blockers().isEmpty()) {
			throw new IllegalArgumentException(inspection.blockers().getFirst().message());
		}
		Long mutationId = pottingVoidPort.compensate(operationId, requestKey, portEffects,
				operation.getPlannedStartDate(), reason, false);
		var now = support.now();
		effects.forEach(effect -> effect.cancel(now));
		operation.voidCompletedInboundRegistration(now, reason, requestKey, mutationId);
	}

	private String batchRequestKey(String key, Long operationId) {
		return "batch-" + java.util.UUID.nameUUIDFromBytes(
				("BATCH_VOID:" + key + ":" + operationId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private String relatedRequestKey(String requestKey, Long operationId) {
		String suffix = "-related-" + operationId;
		int prefixLength = Math.max(1, 100 - suffix.length());
		return requestKey.substring(0, Math.min(prefixLength, requestKey.length())) + suffix;
	}

	private record CancellationInspection(WorkOperation operation, List<WorkOperation> relatedOperations,
			List<Long> mutationIds,
			List<WorkOperationCancellationEligibilityResponse.AffectedOrchidGroup> affectedOrchidGroups,
			List<WorkOperationCancellationEligibilityResponse.Blocker> blockers) {

		private boolean cancellable() {
			return blockers.isEmpty();
		}

		private WorkOperationCancellationEligibilityResponse toResponse() {
			List<WorkOperationCancellationEligibilityResponse.AffectedOperation> affectedOperations = new ArrayList<>();
			affectedOperations.add(toAffectedOperation(operation, true));
			relatedOperations.forEach(related -> affectedOperations.add(toAffectedOperation(related, false)));
			return new WorkOperationCancellationEligibilityResponse(operation.getId(), cancellable(),
					List.copyOf(affectedOperations), affectedOrchidGroups, blockers);
		}

		private WorkOperationCancellationEligibilityResponse.AffectedOperation toAffectedOperation(WorkOperation item,
				boolean primary) {
			return new WorkOperationCancellationEligibilityResponse.AffectedOperation(item.getId(), item.getTitle(),
					item.getWorkType().getName(), item.getPlannedStartDate(), primary);
		}
	}

}
