package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectProcessor;
import com.greenhouse.backend.work.application.operation.WorkOperationView;
import com.greenhouse.backend.work.application.target.ResolvedWorkTarget;
import com.greenhouse.backend.work.application.target.WorkTargetResolver;
import com.greenhouse.backend.work.application.target.WorkTargetSelection;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroupRelationType;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
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

	private final WorkCommandReceipts receipts;

	public WorkOperationView executeForTarget(String requestKey, String workTypeCode, String title, LocalDate workDate,
			String worker, String memo, Long orchidGroupId, Map<String, Object> details, Object payload) {
		String key = WorkCommandReceipts.normalizeKey(requestKey);
		String actor = support.actor(worker);
		var command = new ImmediateCommand(workTypeCode, title, workDate, actor, memo, orchidGroupId, details, payload);
		var ids = receipts.execute("IMMEDIATE", key, command, () -> List
			.of(executeNewForTarget(key, workTypeCode, title, workDate, actor, memo, orchidGroupId, details, payload)));
		return queryService.get(ids.getFirst());
	}

	public WorkOperationView execute(String requestKey, String workTypeCode, String title, LocalDate workDate,
			String worker, String memo, Map<String, Object> details, Object payload) {
		String key = WorkCommandReceipts.normalizeKey(requestKey);
		String actor = support.actor(worker);
		var command = new ImmediateCommand(workTypeCode, title, workDate, actor, memo, null, details, payload);
		var ids = receipts.execute("IMMEDIATE", key, command,
				() -> List.of(executeNew(key, workTypeCode, title, workDate, actor, memo, details, payload)));
		return queryService.get(ids.getFirst());
	}

	private record ImmediateCommand(String workTypeCode, String title, LocalDate workDate, String worker, String memo,
			Long orchidGroupId, Map<String, Object> details, Object payload) {
	}

	private Long executeNewForTarget(String requestKey, String workTypeCode, String title, LocalDate workDate,
			String worker, String memo, Long orchidGroupId, Map<String, Object> details, Object payload) {
		worker = support.actor(worker);
		if (operationRepository.findByRequestKey(requestKey).isPresent()) {
			throw new com.greenhouse.backend.common.exception.ConflictException("IDEMPOTENCY_REPLAY_UNAVAILABLE",
					"과거 요청 원문이 없어 재실행 내용을 확인할 수 없습니다. 기존 작업을 조회해 주세요.");
		}

		ResolvedWorkTarget resolved = workTargetResolver.getCurrent(orchidGroupId);
		WorkTargetSelection targetSelection = WorkTargetSelection.orchidGroup(orchidGroupId);
		WorkOperation operation = new WorkOperation(workTypeService.getByCode(workTypeCode), title, workDate, workDate,
				targetSelection.sourceScopeType(), targetSelection.sourceScopeId(), targetSelection.conditionSnapshot(),
				details, worker, memo, support.now());
		operation.assignRequestKey(requestKey);
		aggregateCreator.createForOrchidGroups(operation, List.of(resolved), targetSelection.inclusionSource(),
				targetSelection.sourceScopeId());
		var execution = executionRepository.findByTargetWorkOperationIdOrderByIdAsc(operation.getId()).getFirst();
		LocalDateTime executedAt = support.now();
		operation.start(executedAt);
		var result = workEffectProcessor.apply(operation, execution.getTarget(),
				new WorkEffectCommand(executedAt, worker, details, payload));
		execution.completeWithEffect(executedAt, worker, result.resultDetails());
		operation.complete(executedAt);
		return operation.getId();
	}

	private Long executeNew(String requestKey, String workTypeCode, String title, LocalDate workDate, String worker,
			String memo, Map<String, Object> details, Object payload) {
		worker = support.actor(worker);
		if (operationRepository.findByRequestKey(requestKey).isPresent()) {
			throw new com.greenhouse.backend.common.exception.ConflictException("IDEMPOTENCY_REPLAY_UNAVAILABLE",
					"과거 요청 원문이 없어 재실행 내용을 확인할 수 없습니다. 기존 작업을 조회해 주세요.");
		}

		WorkOperation operation = new WorkOperation(workTypeService.getByCode(workTypeCode), title, workDate, workDate,
				WorkSourceScopeType.NONE, null, Map.of(), details, worker, memo, support.now());
		operation.assignRequestKey(requestKey);
		operationRepository.save(operation);
		LocalDateTime executedAt = support.now();
		operation.start(executedAt);
		workEffectProcessor.apply(operation, null, new WorkEffectCommand(executedAt, worker, details, payload));
		operation.complete(executedAt);
		return operation.getId();
	}

	@Transactional(readOnly = true)
	public List<Long> getResultOrchidGroupIds(Long operationId) {
		validateWorkType(operationId, WorkTypeDefinition.MULTI_CREATE.name(), "다중 생성 작업만 생성 결과를 조회할 수 있습니다.");
		return effectOrchidGroupRepository.findByWorkAppliedEffectWorkOperationIdOrderByIdAsc(operationId)
			.stream()
			.map(link -> link.getOrchidGroupId())
			.toList();
	}

	@Transactional(readOnly = true)
	public List<Long> getStructureChangeResultOrchidGroupIds(Long operationId, String workTypeCode) {
		validateWorkType(operationId, workTypeCode, "요청한 구조 변경 작업 유형과 일치하지 않습니다.");
		return effectOrchidGroupRepository
			.findByWorkAppliedEffectWorkOperationIdAndRelationTypeOrderByIdAsc(operationId,
					WorkEffectOrchidGroupRelationType.RESULT)
			.stream()
			.map(link -> link.getOrchidGroupId())
			.toList();
	}

	public WorkOperationView cancelMultiCreate(Long operationId) {
		WorkOperation operation = operationRepository.findWithWorkTypeById(operationId)
			.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
		if (!WorkTypeDefinition.MULTI_CREATE.name().equals(operation.getWorkType().getCode())) {
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

}
