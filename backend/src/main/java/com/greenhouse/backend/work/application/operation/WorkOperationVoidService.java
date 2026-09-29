package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationRelationType;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import com.greenhouse.backend.work.domain.operation.WorkTypeWorkflow;
import com.greenhouse.backend.work.dto.operation.WorkOperationVoidEligibilityResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationVoidRequest;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkOperationVoidService {

	private final WorkOperationRepository operationRepository;
	private final WorkAppliedEffectRepository effectRepository;
	private final StructureChangeVoidPort structureChangeVoidPort;
	private final PottingVoidPort pottingVoidPort;
	private final WorkOperationQueryService queryService;
	private final WorkOperationSupport support;

	@Transactional(readOnly = true)
	public WorkOperationVoidEligibilityResponse eligibility(Long operationId) {
		var operation = operationRepository.findWithWorkTypeById(operationId)
			.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
		var blockers = new ArrayList<WorkOperationVoidEligibilityResponse.Blocker>();
		List<Long> relatedWorkOperationIds = new ArrayList<>();
		if (operation.getStatus() == WorkOperationStatus.VOIDED) {
			blockers.add(new WorkOperationVoidEligibilityResponse.Blocker("ALREADY_VOIDED", "이미 무효화된 작업입니다.", 1));
		}
		else if (operation.getRelationType() == WorkOperationRelationType.MOVEMENT_DISCARD) {
			relatedWorkOperationIds.add(operation.getParentOperation().getId());
			blockers.add(new WorkOperationVoidEligibilityResponse.Blocker("VOID_WITH_PARENT_MOVEMENT",
					"이 폐기는 연관된 자리 이동 작업에서 함께 무효화해야 합니다.", 1));
		}
		else if ((operation.getStatus() != WorkOperationStatus.COMPLETED
				&& operation.getStatus() != WorkOperationStatus.CORRECTED)
				|| !operation.getWorkType().supportsMutationVoid()) {
			blockers.add(new WorkOperationVoidEligibilityResponse.Blocker("UNSUPPORTED_OPERATION",
					"완료된 구조 변경·폐기·포트 작업만 무효화할 수 있습니다.", 1));
		}
		var relatedDiscards = operation.getRelationType() == null
				? operationRepository.findByParentOperationIdAndRelationTypeOrderByIdAsc(operationId,
						WorkOperationRelationType.MOVEMENT_DISCARD)
				: List.<WorkOperation>of();
		relatedDiscards.forEach(discard -> {
			relatedWorkOperationIds.add(discard.getId());
			if (discard.getStatus() != WorkOperationStatus.COMPLETED) {
				blockers.add(new WorkOperationVoidEligibilityResponse.Blocker("RELATED_DISCARD_NOT_COMPLETED",
						"연관된 이동 후 잔여 난 폐기 작업의 상태가 완료가 아닙니다.", 1));
			}
		});
		List<Long> targetOperationIds = new ArrayList<>();
		targetOperationIds.add(operationId);
		targetOperationIds.addAll(relatedWorkOperationIds);
		var effects = targetOperationIds.stream()
			.flatMap(id -> effectRepository.findByWorkOperationIdOrderByIdAsc(id).stream())
			.toList();
		List<Long> mutationIds = effects.stream()
			.map(effect -> effect.getMutationId()).filter(Objects::nonNull).distinct().sorted().toList();
		boolean potting = operation.getWorkType().workflow() == WorkTypeWorkflow.POTTING;
		List<Long> sourceIds;
		List<Long> resultIds;
		List<StructureChangeVoidPort.Blocker> inspectionBlockers;
		if (potting) {
			var inspection = pottingVoidPort.inspect(operationId,
					effects.stream()
						.map(effect -> new PottingVoidPort.Effect(
								effect.getTarget() == null ? null : effect.getTarget().getInboundRecordId(),
								effect.getMutationId()))
						.toList());
			sourceIds = List.of();
			resultIds = inspection.resultOrchidGroupIds();
			inspectionBlockers = inspection.blockers();
		}
		else {
			var inspection = structureChangeVoidPort.inspect(operationId, mutationIds);
			sourceIds = inspection.sourceOrchidGroupIds();
			resultIds = inspection.resultOrchidGroupIds();
			inspectionBlockers = inspection.blockers();
		}
		inspectionBlockers
			.forEach(blocker -> blockers.add(new WorkOperationVoidEligibilityResponse.Blocker(blocker.code(),
					blocker.message(), blocker.count())));
		return new WorkOperationVoidEligibilityResponse(operationId, blockers.isEmpty(), mutationIds, sourceIds,
				resultIds, List.copyOf(relatedWorkOperationIds), List.copyOf(blockers));
	}

	public WorkOperationView voidOperation(Long operationId, WorkOperationVoidRequest request) {
		var operation = operationRepository.findWithWorkTypeById(operationId)
			.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
		String requestKey = support.normalizeRequired(request.idempotencyKey());
		if (operation.getStatus() == WorkOperationStatus.VOIDED) {
			if (!requestKey.equals(operation.getVoidRequestKey())) {
				throw new IllegalArgumentException("이미 다른 요청으로 무효화된 작업입니다.");
			}
			return queryService.get(operationId);
		}
		var eligibility = eligibility(operationId);
		if (!eligibility.voidable()) {
			throw new IllegalArgumentException(eligibility.blockers().getFirst().message());
		}
		String reason = support.normalizeRequired(request.reason());
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
			mutationId = structureChangeVoidPort.compensate(operationId, requestKey, eligibility.mutationIds(),
					operation.getPlannedStartDate(), reason);
		}
		var now = support.now();
		List<WorkOperation> relatedDiscards = operationRepository
			.findByParentOperationIdAndRelationTypeOrderByIdAsc(operationId,
					WorkOperationRelationType.MOVEMENT_DISCARD);
		for (int index = relatedDiscards.size() - 1; index >= 0; index--) {
			var discard = relatedDiscards.get(index);
			effectRepository.findByWorkOperationIdOrderByIdAsc(discard.getId())
				.forEach(effect -> effect.cancel(now));
			discard.voidCompletedMutationWork(now, reason, relatedRequestKey(requestKey, discard.getId()), mutationId);
		}
		effectRepository.findByWorkOperationIdOrderByIdAsc(operationId).forEach(effect -> effect.cancel(now));
		operation.voidCompletedMutationWork(now, reason, requestKey, mutationId);
		return queryService.get(operationId);
	}

	public void voidInboundRegistration(Long operationId, WorkOperationVoidRequest request) {
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
					effect.getTarget() == null ? null : effect.getTarget().getInboundRecordId(), effect.getMutationId()))
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

	private String relatedRequestKey(String requestKey, Long operationId) {
		String suffix = "-related-" + operationId;
		int prefixLength = Math.max(1, 100 - suffix.length());
		return requestKey.substring(0, Math.min(prefixLength, requestKey.length())) + suffix;
	}
}
