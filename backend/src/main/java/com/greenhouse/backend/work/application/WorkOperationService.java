package com.greenhouse.backend.work.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
import com.greenhouse.backend.work.domain.WorkTargetExecution;
import com.greenhouse.backend.work.domain.WorkTargetInclusionSource;
import com.greenhouse.backend.work.dto.OrchidGroupWorkHistoryResponse;
import com.greenhouse.backend.work.dto.WorkOperationCreateRequest;
import com.greenhouse.backend.work.dto.WorkOperationResponse;
import com.greenhouse.backend.work.dto.WorkOperationTargetResponse;
import com.greenhouse.backend.work.dto.WorkTargetPreviewRequest;
import com.greenhouse.backend.work.dto.WorkTargetPreviewResponse;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.repository.WorkRecordRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
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

	@Transactional(readOnly = true)
	public WorkTargetPreviewResponse preview(WorkTargetPreviewRequest request) {
		List<ResolvedWorkTarget> groups = workTargetResolver.resolve(request.scopeType(), request.scopeId());
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
		var workType = workTypeService.getActiveForCreate(request.workTypeId());
		if (!"PESTICIDE".equals(workType.getCode())) {
			throw new IllegalArgumentException("초기 작업 실행 모델은 농약 작업만 지원합니다.");
		}

		List<ResolvedWorkTarget> resolved = workTargetResolver.resolve(request.sourceScopeType(), request.sourceScopeId());
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

		Map<String, Object> conditionSnapshot = Map.of("houseId", request.sourceScopeId());
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
						WorkTargetInclusionSource.HOUSE,
						request.sourceScopeId(),
						group.varietyId(),
						group.varietyName(),
						group.ageYear(),
						group.potSize(),
						group.quantity(),
						group.location()))
				.toList();
		workOperationTargetRepository.saveAll(targets);
		workTargetExecutionRepository.saveAll(targets.stream().map(WorkTargetExecution::new).toList());
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
		var responses = targets.stream()
				.map(target -> WorkOperationTargetResponse.from(target, executions.get(target.getId()).getStatus()))
				.toList();
		return WorkOperationResponse.from(operation, responses);
	}

	public WorkOperationResponse complete(Long operationId) {
		WorkOperation operation = workOperationRepository.findWithWorkTypeById(operationId)
				.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
		LocalDateTime completedAt = LocalDateTime.now();
		operation.complete(completedAt);
		workTargetExecutionRepository.findByTargetWorkOperationIdOrderByIdAsc(operationId)
				.forEach(execution -> execution.complete(completedAt, operation.getWorker()));
		return get(operationId);
	}

	@Transactional(readOnly = true)
	public List<OrchidGroupWorkHistoryResponse> getOrchidGroupHistory(Long orchidGroupId) {
		Map<String, Object> currentLocation = workTargetResolver.getCurrent(orchidGroupId).location();
		List<WorkOperationTarget> targets = workOperationTargetRepository
				.findByOrchidGroupIdAndExcludedAtIsNullOrderByWorkOperationPlannedStartDateDescWorkOperationIdDesc(
						orchidGroupId);
		var operationHistory = targets.stream()
				.map(target -> OrchidGroupWorkHistoryResponse.from(target, currentLocation))
				.toList();
		var legacyHistory = workRecordRepository
				.findByTargetTypeAndTargetIdOrderByWorkDateDescIdDesc("ORCHID_GROUP", orchidGroupId)
				.stream()
				.map(record -> OrchidGroupWorkHistoryResponse.fromLegacy(record, currentLocation))
				.toList();
		return java.util.stream.Stream.concat(operationHistory.stream(), legacyHistory.stream())
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
