package com.greenhouse.backend.work.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectProcessor;
import com.greenhouse.backend.work.domain.WorkEffectOrchidGroupRelationType;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationStatus;
import com.greenhouse.backend.work.domain.WorkSourceScopeType;
import com.greenhouse.backend.work.domain.WorkTargetInclusionSource;
import com.greenhouse.backend.work.domain.WorkType;
import com.greenhouse.backend.work.dto.WorkOperationResponse;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ImmediateWorkExecutionService {

	private final WorkTypeService workTypeService;
	private final WorkEffectProcessor workEffectProcessor;
	private final WorkOperationRepository operationRepository;
	private final WorkEffectOrchidGroupRepository effectOrchidGroupRepository;
	private final WorkAppliedEffectRepository appliedEffectRepository;
	private final WorkTargetResolver workTargetResolver;
	private final WorkOperationAggregateCreator aggregateCreator;
	private final WorkTargetExecutionRepository executionRepository;
	private final WorkOperationQueryService queryService;
	private final WorkOperationSupport support;

	public WorkOperationResponse executeForTarget(
			String requestKey,
			String workTypeCode,
			String title,
			LocalDate workDate,
			String worker,
			String memo,
			Long orchidGroupId,
			Map<String, Object> details,
			Object payload) {
		var existing = operationRepository.findByRequestKey(requestKey);
		if (existing.isPresent()) {
			validateRequestKeyWorkType(existing.get(), workTypeCode);
			return queryService.get(existing.get().getId());
		}

		ResolvedWorkTarget resolved = workTargetResolver.getCurrent(orchidGroupId);
		WorkOperation operation = new WorkOperation(
				workTypeService.getByCode(workTypeCode), title, workDate, workDate,
				WorkSourceScopeType.ORCHID_GROUP, orchidGroupId, Map.of(), details, worker, memo,
				support.now());
		operation.assignRequestKey(requestKey);
		aggregateCreator.createForOrchidGroups(
				operation, List.of(resolved), WorkTargetInclusionSource.DIRECT, orchidGroupId);
		var execution = executionRepository
				.findByTargetWorkOperationIdOrderByIdAsc(operation.getId()).getFirst();
		LocalDateTime executedAt = support.now();
		operation.start(executedAt);
		var result = workEffectProcessor.apply(
				operation,
				execution.getTarget(),
				new WorkEffectCommand(executedAt, worker, details, payload));
		execution.completeWithEffect(executedAt, worker, result.resultDetails());
		operation.complete(executedAt);
		return queryService.get(operation.getId());
	}

	public WorkOperationResponse execute(
			String requestKey,
			String workTypeCode,
			String title,
			LocalDate workDate,
			String worker,
			String memo,
			Map<String, Object> details,
			Object payload) {
		var existing = operationRepository.findByRequestKey(requestKey);
		if (existing.isPresent()) {
			validateRequestKeyWorkType(existing.get(), workTypeCode);
			return queryService.get(existing.get().getId());
		}

		WorkOperation operation = new WorkOperation(
				workTypeService.getByCode(workTypeCode), title, workDate, workDate,
				WorkSourceScopeType.NONE, null, Map.of(), details, worker, memo, support.now());
		operation.assignRequestKey(requestKey);
		operationRepository.save(operation);
		LocalDateTime executedAt = support.now();
		operation.start(executedAt);
		workEffectProcessor.apply(
				operation, null, new WorkEffectCommand(executedAt, worker, details, payload));
		operation.complete(executedAt);
		return queryService.get(operation.getId());
	}

	@Transactional(readOnly = true)
	public List<Long> getResultOrchidGroupIds(Long operationId) {
		validateWorkType(operationId, WorkType.MULTI_CREATE_CODE, "다중 생성 작업만 생성 결과를 조회할 수 있습니다.");
		return effectOrchidGroupRepository.findByWorkAppliedEffectWorkOperationIdOrderByIdAsc(operationId)
				.stream().map(link -> link.getOrchidGroupId()).toList();
	}

	@Transactional(readOnly = true)
	public List<Long> getStructureChangeResultOrchidGroupIds(Long operationId, String workTypeCode) {
		validateWorkType(operationId, workTypeCode, "요청한 구조 변경 작업 유형과 일치하지 않습니다.");
		return effectOrchidGroupRepository
				.findByWorkAppliedEffectWorkOperationIdAndRelationTypeOrderByIdAsc(
						operationId, WorkEffectOrchidGroupRelationType.RESULT)
				.stream().map(link -> link.getOrchidGroupId()).toList();
	}

	public WorkOperationResponse cancelMultiCreate(Long operationId) {
		WorkOperation operation = operationRepository.findWithWorkTypeById(operationId)
				.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
		if (!WorkType.MULTI_CREATE_CODE.equals(operation.getWorkType().getCode())) {
			throw new IllegalArgumentException("다중 생성 작업만 결과 취소할 수 있습니다.");
		}
		if (operation.getStatus() == WorkOperationStatus.CANCELED) {
			return queryService.get(operationId);
		}
		operation.cancelCompletedStructureChange();
		appliedEffectRepository.findByWorkOperationIdAndEffectKey(operationId, "OPERATION")
				.orElseThrow(() -> new NotFoundException("작업 효과를 찾을 수 없습니다."))
				.cancel(support.now());
		return queryService.get(operationId);
	}

	private void validateWorkType(Long operationId, String workTypeCode, String message) {
		WorkOperation operation = operationRepository.findWithWorkTypeById(operationId)
				.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
		if (!workTypeCode.equals(operation.getWorkType().getCode())) {
			throw new IllegalArgumentException(message);
		}
	}

	private void validateRequestKeyWorkType(WorkOperation operation, String workTypeCode) {
		if (!operation.getWorkType().getCode().equals(workTypeCode)) {
			throw new IllegalArgumentException("요청 식별자가 다른 작업 유형에서 이미 사용되었습니다.");
		}
	}
}
