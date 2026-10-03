package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.application.target.InboundPottingPlanGateway;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class WorkOperationLockService {

	private final WorkOperationRepository operationRepository;

	private final WorkOperationTargetRepository targetRepository;

	private final InboundPottingPlanGateway inboundGateway;

	public WorkOperation lock(Long operationId) {
		return lockAll(java.util.List.of(operationId)).getFirst();
	}

	public java.util.List<WorkOperation> lockAll(java.util.Collection<Long> operationIds) {
		var ids = operationIds.stream().distinct().sorted().toList();
		if (ids.isEmpty())
			return java.util.List.of();
		var inboundIds = targetRepository.findInboundRecordIdsIn(ids);
		if (!inboundIds.isEmpty()) {
			inboundGateway.lockForPottingExecution(inboundIds);
		}
		var operations = operationRepository.findAllForUpdateByIdIn(ids);
		if (operations.size() != ids.size())
			throw new NotFoundException("작업을 찾을 수 없습니다.");
		return operations;
	}

	public void lockInboundPlans(java.util.List<Long> requestedIds) {
		var statuses = java.util.Set.of(com.greenhouse.backend.work.domain.operation.WorkOperationStatus.PLANNED,
				com.greenhouse.backend.work.domain.operation.WorkOperationStatus.IN_PROGRESS,
				com.greenhouse.backend.work.domain.operation.WorkOperationStatus.PAUSED);
		var operationIds = targetRepository.findActivePottingOperationIds(requestedIds, statuses);
		var siblingIds = operationIds.isEmpty() ? java.util.List.<Long>of()
				: targetRepository.findInboundRecordIdsIn(operationIds);
		var inboundIds = java.util.stream.Stream.concat(requestedIds.stream(), siblingIds.stream())
			.distinct()
			.sorted()
			.toList();
		inboundGateway.lockForPottingExecution(inboundIds);
		if (!operationIds.equals(targetRepository.findActivePottingOperationIds(requestedIds, statuses))) {
			throw new com.greenhouse.backend.common.exception.ConflictException("WORK_PLAN_CHANGED",
					"포트 작업 계획이 변경되었습니다. 최신 상태로 다시 요청해주세요.");
		}
		if (!operationIds.isEmpty())
			operationRepository.findAllForUpdateByIdIn(operationIds);
	}

}
