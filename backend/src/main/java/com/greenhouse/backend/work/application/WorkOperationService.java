package com.greenhouse.backend.work.application;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
import com.greenhouse.backend.work.domain.WorkOperationStatus;
import com.greenhouse.backend.work.domain.WorkSourceScopeType;
import com.greenhouse.backend.work.domain.WorkTargetExecution;
import com.greenhouse.backend.work.domain.WorkTargetInclusionSource;
import com.greenhouse.backend.work.dto.OrchidGroupWorkHistoryResponse;
import com.greenhouse.backend.work.dto.WorkOperationCreateRequest;
import com.greenhouse.backend.work.dto.WorkOperationResponse;
import com.greenhouse.backend.work.dto.WorkOperationTargetResponse;
import com.greenhouse.backend.work.dto.WorkTargetPreviewRequest;
import com.greenhouse.backend.work.dto.WorkTargetPreviewResponse;
import com.greenhouse.backend.work.dto.WorkTargetExecutionRequest;
import com.greenhouse.backend.work.repository.WorkOperationRepository;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import com.greenhouse.backend.work.repository.WorkRecordRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
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
		var workType = workTypeService.getActiveForCreate(request.workTypeId());
		if (!"PESTICIDE".equals(workType.getCode())) {
			throw new IllegalArgumentException("초기 작업 실행 모델은 농약 작업만 지원합니다.");
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
				.map(target -> WorkOperationTargetResponse.from(target, executions.get(target.getId())))
				.toList();
		return WorkOperationResponse.from(operation, responses);
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
		if (executions.stream().anyMatch(execution -> execution.getStatus()
				== com.greenhouse.backend.work.domain.WorkTargetExecutionStatus.COMPLETED)) {
			throw new IllegalArgumentException("완료된 대상이 있는 작업은 취소할 수 없습니다.");
		}
		LocalDateTime canceledAt = LocalDateTime.now();
		operation.cancel();
		executions.forEach(execution -> execution.cancel(canceledAt));
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
		validateOperationInProgress(operationId);
		findExecution(operationId, targetId).complete(
				LocalDateTime.now(), normalize(request.worker()), request.resultDetails());
		return get(operationId);
	}

	public WorkOperationResponse skipTarget(
			Long operationId, Long targetId, WorkTargetExecutionRequest request) {
		validateOperationInProgress(operationId);
		findExecution(operationId, targetId).skip(
				LocalDateTime.now(), normalize(request.worker()), request.resultDetails());
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

	private WorkOperation findOperation(Long operationId) {
		return workOperationRepository.findWithWorkTypeById(operationId)
				.orElseThrow(() -> new NotFoundException("작업을 찾을 수 없습니다."));
	}

	private WorkTargetExecution findExecution(Long operationId, Long targetId) {
		return workTargetExecutionRepository.findByTargetIdAndTargetWorkOperationId(targetId, operationId)
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
			case HOUSE, USER_COLLECTION -> {
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
		return new WorkTargetSelection(scopeType, scopeId, normalizedKey, normalizedIds);
	}

	private Map<String, Object> conditionSnapshot(WorkTargetSelection selection) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		switch (selection.scopeType()) {
			case HOUSE -> snapshot.put("houseId", selection.scopeId());
			case USER_COLLECTION -> snapshot.put("collectionId", selection.scopeId());
			case DERIVED_GROUP -> snapshot.put("groupKey", selection.scopeKey());
			case MANUAL_SELECTION -> snapshot.put("orchidGroupIds", selection.orchidGroupIds());
			default -> throw new IllegalArgumentException("아직 지원하지 않는 작업 대상 유형입니다.");
		}
		return snapshot;
	}

	private WorkTargetInclusionSource inclusionSource(WorkSourceScopeType scopeType) {
		return switch (scopeType) {
			case HOUSE -> WorkTargetInclusionSource.HOUSE;
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
