package com.greenhouse.backend.work.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
import com.greenhouse.backend.work.domain.WorkOperationStatus;
import com.greenhouse.backend.work.domain.WorkSourceScopeType;
import com.greenhouse.backend.work.domain.WorkTargetExecution;
import com.greenhouse.backend.work.domain.WorkTargetExecutionStatus;
import com.greenhouse.backend.work.domain.WorkTargetInclusionSource;
import com.greenhouse.backend.work.domain.WorkTargetReferenceType;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectProcessor;
import com.greenhouse.backend.work.dto.OrchidGroupWorkHistoryResponse;
import com.greenhouse.backend.work.dto.InboundPottingPlanCreateRequest;
import com.greenhouse.backend.work.dto.InboundPottingCandidateResponse;
import com.greenhouse.backend.work.dto.WorkOperationCreateRequest;
import com.greenhouse.backend.work.dto.WorkOperationResponse;
import com.greenhouse.backend.work.dto.WorkOperationTargetResponse;
import com.greenhouse.backend.work.dto.WorkTargetPreviewRequest;
import com.greenhouse.backend.work.dto.WorkTargetPreviewResponse;
import com.greenhouse.backend.work.dto.WorkTargetExecutionRequest;
import com.greenhouse.backend.work.dto.StructureChangeExecutionRequest;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.repository.WorkRecordRepository;
import com.greenhouse.backend.work.repository.WorkEffectOrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
public class WorkOperationService {

	private final WorkTargetResolver workTargetResolver;
	private final WorkTypeService workTypeService;
	private final WorkOperationRepository workOperationRepository;
	private final WorkOperationTargetRepository workOperationTargetRepository;
	private final WorkTargetExecutionRepository workTargetExecutionRepository;
	private final WorkRecordRepository workRecordRepository;
	private final WorkEffectOrchidGroupRepository workEffectOrchidGroupRepository;
	private final WorkEffectProcessor workEffectProcessor;
	private final WorkAppliedEffectRepository workAppliedEffectRepository;
	private final InboundPottingPlanGateway inboundPottingPlanGateway;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Transactional(readOnly = true)
	public WorkTargetPreviewResponse preview(WorkTargetPreviewRequest request) {
		WorkTargetSelection selection = selection(
				request.scopeType(), request.scopeId(), request.scopeKey(), request.orchidGroupIds());
		List<ResolvedWorkTarget> groups = workTargetResolver.resolve(selection);
		var targets = groups.stream()
				.map(WorkOperationTargetResponse::preview)
				.toList();
		return new WorkTargetPreviewResponse(
				targets.size(),
				groups.stream().mapToInt(ResolvedWorkTarget::quantity).sum(),
				targets);
	}

	public WorkOperationResponse create(WorkOperationCreateRequest request) {
		validateDates(request.plannedStartDate(), request.plannedEndDate());
		var workType = workTypeService.getActiveForPlan(request.workTypeId());
		if (workType.effectKind() != com.greenhouse.backend.work.domain.WorkEffectKind.RECORD_ONLY
				&& !com.greenhouse.backend.work.domain.WorkType.REPOT_CODE.equals(workType.getCode())
				&& !com.greenhouse.backend.work.domain.WorkType.MOVEMENT_CODE.equals(workType.getCode())
				&& !com.greenhouse.backend.work.domain.WorkType.DIVIDE_CODE.equals(workType.getCode())
				&& !com.greenhouse.backend.work.domain.WorkType.MERGE_CODE.equals(workType.getCode())
				&& !com.greenhouse.backend.work.domain.WorkType.DISCARD_CODE.equals(workType.getCode())) {
			throw new IllegalArgumentException("이 작업 유형은 난 묶음 대상 기간 작업으로 만들 수 없습니다.");
		}

		WorkTargetSelection selection = selection(
				request.sourceScopeType(), request.sourceScopeId(), request.sourceScopeKey(),
				request.sourceOrchidGroupIds());
		List<ResolvedWorkTarget> resolved = workTargetResolver.resolve(selection);
		Set<Long> excludedIds = request.excludedOrchidGroupIds() == null
				? Set.of()
				: new HashSet<>(request.excludedOrchidGroupIds());
		Set<Long> resolvedIds = resolved.stream().map(ResolvedWorkTarget::orchidGroupId).collect(Collectors.toSet());
		if (!resolvedIds.containsAll(excludedIds)) {
			throw new IllegalArgumentException("제외 대상은 현재 해석된 난 묶음에 포함되어야 합니다.");
		}
		List<ResolvedWorkTarget> included = resolved.stream()
				.filter(group -> !excludedIds.contains(group.orchidGroupId()))
				.toList();
		if (included.isEmpty()) {
			throw new IllegalArgumentException("작업 대상 난 묶음이 한 개 이상 필요합니다.");
		}
		if (com.greenhouse.backend.work.domain.WorkType.MERGE_CODE.equals(workType.getCode())) {
			Long varietyId = included.getFirst().varietyId();
			if (varietyId == null || included.stream().anyMatch(group -> !varietyId.equals(group.varietyId()))) {
				throw new IllegalArgumentException("합식은 같은 품종의 난 묶음끼리만 계획할 수 있습니다.");
			}
		}

		Map<String, Object> conditionSnapshot = conditionSnapshot(selection);
		WorkOperation operation = workOperationRepository.save(new WorkOperation(
				workType,
				normalizeRequired(request.title()),
				request.plannedStartDate(),
				request.plannedEndDate(),
				request.sourceScopeType(),
				request.sourceScopeId(),
				conditionSnapshot,
				request.details(),
				normalize(request.worker()),
				normalize(request.memo())));

		List<WorkOperationTarget> targets = included.stream()
				.map(group -> new WorkOperationTarget(
						operation,
						group.orchidGroupId(),
						inclusionSource(request.sourceScopeType()),
						request.sourceScopeId(),
						group.varietyId(),
						group.varietyName(),
						group.ageYear(),
						group.potSizeCode(),
						group.potSize(),
						group.quantity(),
						group.location()))
				.toList();
		workOperationTargetRepository.saveAll(targets);
		workTargetExecutionRepository.saveAll(targets.stream().map(WorkTargetExecution::new).toList());
		return get(operation.getId());
	}

	public WorkOperationResponse createCompletedRecord(WorkOperationCreateRequest request) {
		workTypeService.getActiveForCreate(request.workTypeId());
		WorkOperationResponse created = create(request);
		WorkOperation operation = findOperation(created.id());
		LocalDateTime executedAt = LocalDateTime.now();
		operation.start(executedAt);
		List<WorkTargetExecution> executions = workTargetExecutionRepository
				.findByTargetWorkOperationIdOrderByIdAsc(operation.getId());
		for (WorkTargetExecution execution : executions) {
			var result = workEffectProcessor.apply(
					operation,
					execution.getTarget(),
					new WorkEffectCommand(executedAt, normalize(request.worker()), request.details(), null));
			execution.completeWithEffect(executedAt, normalize(request.worker()), result.resultDetails());
		}
		operation.complete(executedAt);
		return get(operation.getId());
	}

	@Transactional(readOnly = true)
	public List<InboundPottingCandidateResponse> getInboundPottingCandidates() {
		return inboundPottingPlanGateway.findCandidates()
				.stream()
				.map(InboundPottingCandidateResponse::from)
				.toList();
	}

	public WorkOperationResponse createInboundPottingPlan(InboundPottingPlanCreateRequest request) {
		validateDates(request.plannedStartDate(), request.plannedEndDate());
		var workType = workTypeService.getByCode(com.greenhouse.backend.work.domain.WorkType.POTTING_CODE);
		if (!workType.isActive()) {
			throw new IllegalArgumentException("포트 작업 유형이 비활성화되어 있습니다.");
		}
		List<Long> requestedIds = request.inboundRecordIds().stream().distinct().toList();
		for (Long inboundRecordId : requestedIds) {
			if (!workTargetExecutionRepository
					.findActiveInboundPottingForUpdate(inboundRecordId).isEmpty()) {
				throw new IllegalArgumentException("이미 활성 포트 작업 계획에 포함된 입고 기록입니다.");
			}
		}
		var records = inboundPottingPlanGateway.resolve(requestedIds);

		WorkOperation operation = workOperationRepository.save(new WorkOperation(
				workType,
				normalizeRequired(request.title()),
				request.plannedStartDate(),
				request.plannedEndDate(),
				WorkSourceScopeType.INBOUND_RECORD_SELECTION,
				null,
				Map.of("inboundRecordIds", requestedIds),
				Map.of(),
				normalize(request.worker()),
				normalize(request.memo())));
		List<WorkOperationTarget> targets = records.stream()
				.sorted(java.util.Comparator.comparing(record -> requestedIds.indexOf(record.id())))
				.map(record -> WorkOperationTarget.inboundRecord(
							operation,
							record.id(),
							record.varietyId(),
							record.varietyName(),
							record.currentQuantity(0),
							record.potSize(),
							inboundLocation(record)))
				.toList();
		workOperationTargetRepository.saveAll(targets);
		workTargetExecutionRepository.saveAll(targets.stream().map(WorkTargetExecution::new).toList());
		inboundPottingPlanGateway.markPottingPlanned(requestedIds);
		return get(operation.getId());
	}

	@Transactional(readOnly = true)
	public WorkOperationResponse get(Long operationId) {
		WorkOperation operation = workOperationRepository.findWithWorkTypeById(operationId)
				.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
		List<WorkOperationTarget> targets = workOperationTargetRepository
				.findByWorkOperationIdAndExcludedAtIsNullOrderByIdAsc(operationId);
		Map<Long, WorkTargetExecution> executions = workTargetExecutionRepository
				.findByTargetWorkOperationIdOrderByIdAsc(operationId)
				.stream()
				.collect(Collectors.toMap(execution -> execution.getTarget().getId(), Function.identity()));
		Map<Long, InboundPottingPlanTarget> currentInboundById = currentInboundTargets(operation, targets, executions);
		var responses = targets.stream()
				.map(target -> WorkOperationTargetResponse.from(
						target,
						executions.get(target.getId()),
						target.getInboundRecordId() == null
								? null
								: currentInboundById.get(target.getInboundRecordId())))
				.toList();
		return WorkOperationResponse.from(operation, responses);
	}

	@Transactional(readOnly = true)
	public List<WorkOperationResponse> search(
			LocalDate fromDate,
			LocalDate toDate,
			WorkOperationStatus status,
			WorkSourceScopeType scopeType,
			Long scopeId) {
		validateDates(fromDate, toDate);
		if (scopeId != null && scopeType == null) {
			throw new IllegalArgumentException("대상 범위 ID를 조회하려면 대상 범위 유형이 필요합니다.");
		}
		return workOperationRepository.search(fromDate, toDate, status, scopeType, scopeId).stream()
				.map(operation -> get(operation.getId()))
				.toList();
	}

	public WorkOperationResponse complete(Long operationId) {
		WorkOperation operation = workOperationRepository.findWithWorkTypeById(operationId)
				.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
		List<WorkTargetExecution> executions = workTargetExecutionRepository
				.findByTargetWorkOperationIdOrderByIdAsc(operationId);
		if (executions.isEmpty() || executions.stream().anyMatch(execution -> !execution.isTerminalForCompletion())) {
			throw new IllegalArgumentException("모든 작업 대상을 완료하거나 건너뛴 뒤 전체 작업을 완료할 수 있습니다.");
		}
		LocalDateTime completedAt = LocalDateTime.now();
		operation.complete(completedAt);
		return get(operationId);
	}

	public WorkOperationResponse start(Long operationId) {
		WorkOperation operation = findOperation(operationId);
		operation.start(LocalDateTime.now());
		return get(operationId);
	}

	public WorkOperationResponse pause(Long operationId) {
		WorkOperation operation = findOperation(operationId);
		operation.pause();
		return get(operationId);
	}

	public WorkOperationResponse resume(Long operationId) {
		WorkOperation operation = findOperation(operationId);
		operation.resume();
		return get(operationId);
	}

	public WorkOperationResponse cancel(Long operationId) {
		WorkOperation operation = findOperation(operationId);
		List<WorkTargetExecution> executions = workTargetExecutionRepository
				.findByTargetWorkOperationIdOrderByIdAsc(operationId);
		LocalDateTime canceledAt = LocalDateTime.now();
		operation.cancel(canceledAt);
		executions.stream()
				.filter(execution -> execution.getStatus()
						!= com.greenhouse.backend.work.domain.WorkTargetExecutionStatus.COMPLETED)
				.filter(execution -> execution.getStatus()
						!= com.greenhouse.backend.work.domain.WorkTargetExecutionStatus.SKIPPED)
				.forEach(execution -> execution.cancel(canceledAt));
		closeInboundPottingPlans(operation, executions);
		return get(operationId);
	}

	public WorkOperationResponse startTarget(
			Long operationId, Long targetId, WorkTargetExecutionRequest request) {
		validateOperationInProgress(operationId);
		findExecution(operationId, targetId).start(LocalDateTime.now(), normalize(request.worker()));
		return get(operationId);
	}

	public WorkOperationResponse completeTarget(
			Long operationId, Long targetId, WorkTargetExecutionRequest request) {
		WorkTargetExecution execution = findExecutionForUpdate(operationId, targetId);
		if (execution.isEffectApplied()) {
			return get(operationId);
		}
		WorkOperation operation = execution.getTarget().getWorkOperation();
		if (operation.getStatus() != WorkOperationStatus.IN_PROGRESS) {
			throw new IllegalArgumentException("진행 중인 작업에서만 대상을 처리할 수 있습니다.");
		}
		refreshInboundSnapshotForExecution(operation, execution.getTarget());
		LocalDateTime completedAt = LocalDateTime.now();
		String worker = normalize(request.worker());
		var result = workEffectProcessor.apply(
				operation,
				execution.getTarget(),
				new WorkEffectCommand(completedAt, worker, request.resultDetails(), null));
		execution.completeWithEffect(completedAt, worker, result.resultDetails());
		return get(operationId);
	}

	private Map<Long, InboundPottingPlanTarget> currentInboundTargets(
			WorkOperation operation,
			List<WorkOperationTarget> targets,
			Map<Long, WorkTargetExecution> executions) {
		if (!com.greenhouse.backend.work.domain.WorkType.POTTING_CODE.equals(operation.getWorkType().getCode())
				|| !Set.of(
						WorkOperationStatus.PLANNED,
						WorkOperationStatus.IN_PROGRESS,
						WorkOperationStatus.PAUSED)
						.contains(operation.getStatus())) {
			return Map.of();
		}
		List<Long> inboundRecordIds = targets.stream()
				.filter(target -> target.getTargetReferenceType() == WorkTargetReferenceType.INBOUND_RECORD)
				.filter(target -> {
					WorkTargetExecution execution = executions.get(target.getId());
					return execution != null && Set.of(
							WorkTargetExecutionStatus.PENDING,
							WorkTargetExecutionStatus.IN_PROGRESS,
							WorkTargetExecutionStatus.PARTIALLY_COMPLETED)
							.contains(execution.getStatus());
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

	private void refreshInboundSnapshotForExecution(
			WorkOperation operation,
			WorkOperationTarget target) {
		if (!com.greenhouse.backend.work.domain.WorkType.POTTING_CODE.equals(operation.getWorkType().getCode())
				|| target.getTargetReferenceType() != WorkTargetReferenceType.INBOUND_RECORD) {
			return;
		}
		InboundPottingPlanTarget current = inboundPottingPlanGateway
				.findCurrent(List.of(target.getInboundRecordId()))
				.stream()
				.findFirst()
				.orElseThrow(() -> new NotFoundException("포트 작업 대상 입고 기록을 찾을 수 없습니다."));
		target.refreshInboundSnapshot(
				current.varietyId(),
				current.varietyName(),
				current.currentQuantity(target.getQuantitySnapshot()),
				current.potSize(),
				inboundLocation(current));
	}

	private Map<String, Object> inboundLocation(InboundPottingPlanTarget inbound) {
		Map<String, Object> location = new LinkedHashMap<>();
		location.put("tempLocation", inbound.tempLocation());
		location.put("pottingDueDate", inbound.pottingDueDate());
		return location;
	}

	public WorkOperationResponse completeMerge(
			Long operationId, WorkTargetExecutionRequest request) {
		List<WorkTargetExecution> executions = workTargetExecutionRepository
				.findForUpdateByTargetWorkOperationIdOrderByIdAsc(operationId);
		if (executions.size() < 2) {
			throw new IllegalArgumentException("합식은 작업 대상이 두 개 이상 필요합니다.");
		}
		WorkOperation operation = executions.getFirst().getTarget().getWorkOperation();
		if (!com.greenhouse.backend.work.domain.WorkType.MERGE_CODE.equals(operation.getWorkType().getCode())) {
			throw new IllegalArgumentException("합식 작업만 일괄 실행할 수 있습니다.");
		}
		if (executions.stream().allMatch(WorkTargetExecution::isEffectApplied)) {
			return get(operationId);
		}
		if (executions.stream().anyMatch(WorkTargetExecution::isEffectApplied)) {
			throw new IllegalStateException("합식 작업 대상의 효과 적용 상태가 일치하지 않습니다.");
		}
		if (operation.getStatus() != WorkOperationStatus.IN_PROGRESS) {
			throw new IllegalArgumentException("진행 중인 합식 작업만 실행할 수 있습니다.");
		}
		LocalDateTime completedAt = LocalDateTime.now();
		String worker = normalize(request.worker());
		var result = workEffectProcessor.apply(
				operation,
				null,
				new WorkEffectCommand(completedAt, worker, request.resultDetails(), null));
		executions.forEach(execution ->
				execution.completeWithEffect(completedAt, worker, result.resultDetails()));
		return get(operationId);
	}

	public WorkOperationResponse executeStructureChange(
			Long operationId, StructureChangeExecutionRequest request) {
		List<WorkTargetExecution> executions = workTargetExecutionRepository
				.findForUpdateByTargetWorkOperationIdOrderByIdAsc(operationId);
		if (executions.isEmpty()) {
			throw new IllegalArgumentException("구조 변경 작업 대상이 없습니다.");
		}
		WorkOperation operation = executions.getFirst().getTarget().getWorkOperation();
		if (!Set.of(
				com.greenhouse.backend.work.domain.WorkType.REPOT_CODE,
				com.greenhouse.backend.work.domain.WorkType.DIVIDE_CODE,
				com.greenhouse.backend.work.domain.WorkType.MERGE_CODE)
				.contains(operation.getWorkType().getCode())) {
			throw new IllegalArgumentException("분갈이·분주·합식 작업만 회차 실행할 수 있습니다.");
		}
		if (operation.getStatus() != WorkOperationStatus.IN_PROGRESS) {
			throw new IllegalArgumentException("진행 중인 구조 변경 작업만 실행할 수 있습니다.");
		}
		String effectKey = "EXECUTION:" + request.idempotencyKey();
		if (workAppliedEffectRepository.findByWorkOperationIdAndEffectKey(operationId, effectKey).isPresent()) {
			return get(operationId);
		}

		Map<Long, WorkTargetExecution> executionByGroupId = executions.stream()
				.filter(execution -> execution.getTarget().getOrchidGroupId() != null)
				.collect(Collectors.toMap(
						execution -> execution.getTarget().getOrchidGroupId(), Function.identity()));
		Set<Long> requestedIds = request.sources().stream()
				.map(source -> source.sourceOrchidGroupId()).collect(Collectors.toSet());
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

		LocalDateTime executedAt = LocalDateTime.now();
		String worker = normalize(request.worker());
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
					source.inputQuantity(), execution.getTarget().getQuantitySnapshot(),
					executedAt, worker, result.resultDetails());
		});
		return get(operationId);
	}

	public WorkOperationResponse skipTarget(
			Long operationId, Long targetId, WorkTargetExecutionRequest request) {
		validateOperationInProgress(operationId);
		WorkTargetExecution execution = findExecution(operationId, targetId);
		execution.skip(LocalDateTime.now(), normalize(request.worker()), request.resultDetails());
		closeInboundPottingPlans(
				execution.getTarget().getWorkOperation(),
				List.of(execution));
		return get(operationId);
	}

	private void closeInboundPottingPlans(
			WorkOperation operation,
			List<WorkTargetExecution> executions) {
		if (!com.greenhouse.backend.work.domain.WorkType.POTTING_CODE.equals(operation.getWorkType().getCode())) {
			return;
		}
		List<Long> inboundRecordIds = executions.stream()
				.filter(execution -> execution.getStatus()
						!= WorkTargetExecutionStatus.COMPLETED)
				.map(WorkTargetExecution::getTarget)
				.filter(target -> target.getTargetReferenceType() == WorkTargetReferenceType.INBOUND_RECORD)
				.map(WorkOperationTarget::getInboundRecordId)
				.distinct()
				.toList();
		if (!inboundRecordIds.isEmpty()) {
			inboundPottingPlanGateway.closePottingPlan(inboundRecordIds);
		}
	}

	@Transactional(readOnly = true)
	public List<OrchidGroupWorkHistoryResponse> getOrchidGroupHistory(Long orchidGroupId) {
		Map<String, Object> currentLocation = workTargetResolver.getCurrent(orchidGroupId).location();
		List<WorkOperationTarget> targets = workOperationTargetRepository
				.findByOrchidGroupIdAndExcludedAtIsNullOrderByWorkOperationPlannedStartDateDescWorkOperationIdDesc(
						orchidGroupId);
		var operationHistoryById = new java.util.LinkedHashMap<Long, OrchidGroupWorkHistoryResponse>();
		targets.forEach(target -> operationHistoryById.put(
				target.getWorkOperation().getId(), OrchidGroupWorkHistoryResponse.from(target, currentLocation)));
		workEffectOrchidGroupRepository
				.findByOrchidGroupIdOrderByWorkAppliedEffectAppliedAtDescWorkAppliedEffectIdDesc(orchidGroupId)
				.forEach(effectGroup -> operationHistoryById.putIfAbsent(
						effectGroup.getWorkAppliedEffect().getWorkOperation().getId(),
						OrchidGroupWorkHistoryResponse.fromEffect(effectGroup, currentLocation)));
		var legacyHistory = workRecordRepository
				.findByTargetTypeAndTargetIdOrderByWorkDateDescIdDesc("ORCHID_GROUP", orchidGroupId)
				.stream()
				.map(record -> OrchidGroupWorkHistoryResponse.fromLegacy(record, currentLocation))
				.toList();
		return java.util.stream.Stream.concat(operationHistoryById.values().stream(), legacyHistory.stream())
				.sorted(java.util.Comparator.comparing(OrchidGroupWorkHistoryResponse::workDate).reversed()
						.thenComparing(history -> history.workOperationId() == null ? Long.MIN_VALUE : history.workOperationId(),
								java.util.Comparator.reverseOrder()))
				.toList();
	}

	private void validateDates(LocalDate startDate, LocalDate endDate) {
		if (endDate != null && endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("예정 종료일은 예정 시작일보다 빠를 수 없습니다.");
		}
	}

	private WorkOperation findOperation(Long operationId) {
		return workOperationRepository.findWithWorkTypeById(operationId)
				.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
	}

	private WorkTargetExecution findExecution(Long operationId, Long targetId) {
		return workTargetExecutionRepository.findByTargetIdAndTargetWorkOperationId(targetId, operationId)
				.orElseThrow(() -> new NotFoundException("작업 대상을 찾을 수 없습니다."));
	}

	private WorkTargetExecution findExecutionForUpdate(Long operationId, Long targetId) {
		return workTargetExecutionRepository
				.findForUpdateByTargetIdAndTargetWorkOperationId(targetId, operationId)
				.orElseThrow(() -> new NotFoundException("작업 대상을 찾을 수 없습니다."));
	}

	private void validateOperationInProgress(Long operationId) {
		if (findOperation(operationId).getStatus() != WorkOperationStatus.IN_PROGRESS) {
			throw new IllegalArgumentException("진행 중인 작업에서만 대상을 처리할 수 있습니다.");
		}
	}

	private WorkTargetSelection selection(
			WorkSourceScopeType scopeType,
			Long scopeId,
			String scopeKey,
			List<Long> orchidGroupIds) {
		String normalizedKey = normalize(scopeKey);
		List<Long> normalizedIds = orchidGroupIds == null
				? List.of()
				: orchidGroupIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
		switch (scopeType) {
			case FARM -> {
				if (scopeId != null) {
					throw new IllegalArgumentException("전체 농장 작업에는 대상 범위 ID를 지정할 수 없습니다.");
				}
			}
			case HOUSE, PHYSICAL_BED, BED_ZONE, ORCHID_GROUP, USER_COLLECTION -> {
				if (scopeId == null) {
					throw new IllegalArgumentException("선택한 작업 대상의 ID가 필요합니다.");
				}
			}
			case DERIVED_GROUP -> {
				if (normalizedKey == null) {
					throw new IllegalArgumentException("자동 그룹 키가 필요합니다.");
				}
			}
			case MANUAL_SELECTION -> {
				if (normalizedIds.isEmpty()) {
					throw new IllegalArgumentException("직접 선택한 난 묶음이 한 개 이상 필요합니다.");
				}
			}
			default -> throw new IllegalArgumentException("아직 지원하지 않는 작업 대상 유형입니다.");
		}
		if (scopeType == WorkSourceScopeType.ORCHID_GROUP) {
			normalizedIds = List.of(scopeId);
		}
		return new WorkTargetSelection(scopeType, scopeId, normalizedKey, normalizedIds);
	}

	private Map<String, Object> conditionSnapshot(WorkTargetSelection selection) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		switch (selection.scopeType()) {
			case FARM -> snapshot.put("farm", true);
			case HOUSE -> snapshot.put("houseId", selection.scopeId());
			case PHYSICAL_BED -> snapshot.put("physicalBedId", selection.scopeId());
			case BED_ZONE -> snapshot.put("bedZoneId", selection.scopeId());
			case ORCHID_GROUP -> snapshot.put("orchidGroupId", selection.scopeId());
			case USER_COLLECTION -> snapshot.put("collectionId", selection.scopeId());
			case DERIVED_GROUP -> snapshot.put("groupKey", selection.scopeKey());
			case MANUAL_SELECTION -> snapshot.put("orchidGroupIds", selection.orchidGroupIds());
			default -> throw new IllegalArgumentException("아직 지원하지 않는 작업 대상 유형입니다.");
		}
		return snapshot;
	}

	private WorkTargetInclusionSource inclusionSource(WorkSourceScopeType scopeType) {
		return switch (scopeType) {
			case FARM -> WorkTargetInclusionSource.FARM;
			case HOUSE -> WorkTargetInclusionSource.HOUSE;
			case PHYSICAL_BED -> WorkTargetInclusionSource.PHYSICAL_BED;
			case BED_ZONE -> WorkTargetInclusionSource.BED_ZONE;
			case ORCHID_GROUP -> WorkTargetInclusionSource.DIRECT;
			case DERIVED_GROUP -> WorkTargetInclusionSource.DERIVED_GROUP;
			case USER_COLLECTION -> WorkTargetInclusionSource.USER_COLLECTION;
			case MANUAL_SELECTION -> WorkTargetInclusionSource.MANUAL_ADDITION;
			default -> throw new IllegalArgumentException("아직 지원하지 않는 작업 대상 유형입니다.");
		};
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private String normalizeRequired(String value) {
		String normalized = normalize(value);
		if (normalized == null) {
			throw new IllegalArgumentException("필수 문자열 값은 비워둘 수 없습니다.");
		}
		return normalized;
	}
}
