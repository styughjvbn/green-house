package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
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
	private final WorkOperationQueryService queryService;
	private final WorkOperationSupport support;

	@Transactional(readOnly = true)
	public WorkOperationVoidEligibilityResponse eligibility(Long operationId) {
		var operation = operationRepository.findWithWorkTypeById(operationId)
			.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
		var blockers = new ArrayList<WorkOperationVoidEligibilityResponse.Blocker>();
		if (operation.getStatus() == WorkOperationStatus.VOIDED) {
			blockers.add(new WorkOperationVoidEligibilityResponse.Blocker("ALREADY_VOIDED", "이미 무효화된 작업입니다.", 1));
		}
		else if ((operation.getStatus() != WorkOperationStatus.COMPLETED
				&& operation.getStatus() != WorkOperationStatus.CORRECTED)
				|| !operation.getWorkType().supportsStructureResultManagement()) {
			blockers.add(new WorkOperationVoidEligibilityResponse.Blocker("UNSUPPORTED_OPERATION",
					"완료된 구조 변경 작업만 무효화할 수 있습니다.", 1));
		}
		List<Long> mutationIds = effectRepository.findByWorkOperationIdOrderByIdAsc(operationId).stream()
			.map(effect -> effect.getMutationId()).filter(Objects::nonNull).distinct().sorted().toList();
		StructureChangeVoidPort.Inspection inspection = structureChangeVoidPort.inspect(operationId, mutationIds);
		inspection.blockers()
			.forEach(blocker -> blockers.add(new WorkOperationVoidEligibilityResponse.Blocker(blocker.code(),
					blocker.message(), blocker.count())));
		return new WorkOperationVoidEligibilityResponse(operationId, blockers.isEmpty(), mutationIds,
				inspection.sourceOrchidGroupIds(), inspection.resultOrchidGroupIds(), List.copyOf(blockers));
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
		Long mutationId = structureChangeVoidPort.compensate(operationId, requestKey, eligibility.mutationIds(),
				operation.getPlannedStartDate(), reason);
		var now = support.now();
		effectRepository.findByWorkOperationIdOrderByIdAsc(operationId).forEach(effect -> effect.cancel(now));
		operation.voidCompletedStructureChange(now, reason, requestKey, mutationId);
		return queryService.get(operationId);
	}
}
