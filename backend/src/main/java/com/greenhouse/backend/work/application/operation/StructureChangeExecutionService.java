package com.greenhouse.backend.work.application.operation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectProcessor;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.dto.effect.StructureChangeExecutionRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationResponse;
import com.greenhouse.backend.work.dto.target.WorkTargetExecutionRequest;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class StructureChangeExecutionService {

	private static final Set<String> SUPPORTED_TYPES = Set.of(
			WorkType.REPOT_CODE,
			WorkType.DIVIDE_CODE,
			WorkType.MERGE_CODE);

	private final WorkTargetExecutionRepository executionRepository;
	private final WorkAppliedEffectRepository appliedEffectRepository;
	private final WorkEffectProcessor workEffectProcessor;
	private final WorkOperationProgressService progressService;
	private final WorkOperationQueryService queryService;
	private final WorkOperationSupport support;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public WorkOperationResponse completeMerge(
			Long operationId, WorkTargetExecutionRequest request) {
		List<WorkTargetExecution> executions = executionRepository
				.findForUpdateByTargetWorkOperationIdOrderByIdAsc(operationId);
		if (executions.size() < 2) {
			throw new IllegalArgumentException("합식은 작업 대상이 두 개 이상 필요합니다.");
		}
		WorkOperation operation = executions.getFirst().getTarget().getWorkOperation();
		if (!WorkType.MERGE_CODE.equals(operation.getWorkType().getCode())) {
			throw new IllegalArgumentException("합식 작업만 일괄 실행할 수 있습니다.");
		}
		if (executions.stream().allMatch(WorkTargetExecution::isEffectApplied)) {
			return queryService.get(operationId);
		}
		if (executions.stream().anyMatch(WorkTargetExecution::isEffectApplied)) {
			throw new IllegalStateException("합식 작업 대상의 효과 적용 상태가 일치하지 않습니다.");
		}
		validateInProgress(operation);
		LocalDateTime completedAt = support.completionTime(request.completedDate());
		String worker = support.normalize(request.worker());
		var result = workEffectProcessor.apply(
				operation,
				null,
				new WorkEffectCommand(completedAt, worker, request.resultDetails(), null));
		executions.forEach(execution ->
				execution.completeWithEffect(completedAt, worker, result.resultDetails()));
		progressService.completeIfAllTargetsClosed(operation, completedAt);
		return queryService.get(operationId);
	}

	public WorkOperationResponse execute(
			Long operationId, StructureChangeExecutionRequest request) {
		List<WorkTargetExecution> executions = executionRepository
				.findForUpdateByTargetWorkOperationIdOrderByIdAsc(operationId);
		if (executions.isEmpty()) {
			throw new IllegalArgumentException("구조 변경 작업 대상이 없습니다.");
		}
		WorkOperation operation = executions.getFirst().getTarget().getWorkOperation();
		if (!SUPPORTED_TYPES.contains(operation.getWorkType().getCode())) {
			throw new IllegalArgumentException("분갈이·분주·합식 작업만 회차 실행할 수 있습니다.");
		}
		validateInProgress(operation);
		String effectKey = "EXECUTION:" + request.idempotencyKey();
		if (appliedEffectRepository.findByWorkOperationIdAndEffectKey(operationId, effectKey).isPresent()) {
			return queryService.get(operationId);
		}

		Map<Long, WorkTargetExecution> executionByGroupId = executions.stream()
				.filter(execution -> execution.getTarget().getOrchidGroupId() != null)
				.collect(Collectors.toMap(
						execution -> execution.getTarget().getOrchidGroupId(),
						Function.identity()));
		Set<Long> requestedIds = request.sources().stream()
				.map(source -> source.sourceOrchidGroupId())
				.collect(Collectors.toSet());
		if (requestedIds.size() != request.sources().size()
				|| !executionByGroupId.keySet().containsAll(requestedIds)) {
			throw new IllegalArgumentException("실행 원본은 계획에 확정된 난 묶음이어야 하며 중복될 수 없습니다.");
		}
		request.sources().forEach(source -> {
			WorkTargetExecution execution = executionByGroupId.get(source.sourceOrchidGroupId());
			int remaining = execution.getTarget().getQuantitySnapshot() - execution.getProcessedQuantity();
			if (source.inputQuantity() > remaining) {
				throw new IllegalArgumentException("작업 수량은 대상의 계획 잔여 수량보다 클 수 없습니다.");
			}
		});

		LocalDateTime executedAt = support.completionTime(request.completedDate());
		String worker = support.normalize(request.worker());
		Map<String, Object> commandDetails = objectMapper.convertValue(
				request, new TypeReference<Map<String, Object>>() {});
		var result = workEffectProcessor.applyBatch(
				operation,
				request.idempotencyKey(),
				requestedIds.stream().sorted().toList(),
				new WorkEffectCommand(executedAt, worker, commandDetails, request));
		request.sources().forEach(source -> {
			WorkTargetExecution execution = executionByGroupId.get(source.sourceOrchidGroupId());
			execution.recordPartialEffect(
					source.inputQuantity(),
					execution.getTarget().getQuantitySnapshot(),
					executedAt,
					worker,
					result.resultDetails());
		});
		progressService.completeIfAllTargetsClosed(operation, executedAt);
		return queryService.get(operationId);
	}

	private void validateInProgress(WorkOperation operation) {
		if (operation.getStatus() != WorkOperationStatus.IN_PROGRESS) {
			throw new IllegalArgumentException("진행 중인 구조 변경 작업만 실행할 수 있습니다.");
		}
	}
}
