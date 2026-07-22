package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.application.target.ResolvedWorkTarget;
import com.greenhouse.backend.work.application.target.WorkTargetResolver;
import com.greenhouse.backend.work.application.target.WorkTargetSelection;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectProcessor;
import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import com.greenhouse.backend.work.domain.target.WorkTargetInclusionSource;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.dto.operation.WorkOperationBatchCreateRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationCreateRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationResponse;
import com.greenhouse.backend.work.dto.target.WorkOperationTargetResponse;
import com.greenhouse.backend.work.dto.target.WorkTargetPreviewRequest;
import com.greenhouse.backend.work.dto.target.WorkTargetPreviewResponse;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkOperationPlanService {

	private static final Set<String> PERIOD_WORK_TYPES = Set.of(
			WorkType.REPOT_CODE,
			WorkType.MOVEMENT_CODE,
			WorkType.DIVIDE_CODE,
			WorkType.MERGE_CODE,
			WorkType.DISCARD_CODE);
	private static final Set<String> SINGLE_VARIETY_WORK_TYPES = Set.of(
			WorkType.REPOT_CODE,
			WorkType.DIVIDE_CODE,
			WorkType.MERGE_CODE);

	private final WorkTargetResolver workTargetResolver;
	private final WorkTypeService workTypeService;
	private final WorkOperationAggregateCreator aggregateCreator;
	private final WorkTargetExecutionRepository executionRepository;
	private final WorkEffectProcessor workEffectProcessor;
	private final WorkOperationQueryService queryService;
	private final WorkOperationSupport support;

	@Transactional(readOnly = true)
	public WorkTargetPreviewResponse preview(WorkTargetPreviewRequest request) {
		WorkTargetSelection selection = selection(
				request.scopeType(), request.scopeId(), request.scopeKey(), request.orchidGroupIds());
		List<ResolvedWorkTarget> groups = workTargetResolver.resolve(selection);
		var targets = groups.stream().map(WorkOperationTargetResponse::preview).toList();
		return new WorkTargetPreviewResponse(
				targets.size(),
				groups.stream().mapToInt(ResolvedWorkTarget::quantity).sum(),
				targets);
	}

	public WorkOperationResponse create(WorkOperationCreateRequest request) {
		WorkType workType = workTypeService.getActiveForPlan(request.workTypeId());
		validatePeriodWorkType(workType);
		return queryService.get(createOperation(request, workType, resolveIncluded(request)).getId());
	}

	public List<WorkOperationResponse> createBatch(WorkOperationBatchCreateRequest request) {
		WorkOperationCreateRequest operationRequest = request.operation();
		WorkType workType = workTypeService.getActiveForPlan(operationRequest.workTypeId());
		validatePeriodWorkType(workType);
		ResolvedSelection resolvedSelection = resolveIncluded(operationRequest);
		if (!requiresSingleVariety(workType.getCode())) {
			return List.of(queryService.get(
					createOperation(operationRequest, workType, resolvedSelection).getId()));
		}
		List<VarietyTargetGroup> varietyGroups = groupTargetsByVariety(resolvedSelection.included());
		return varietyGroups.stream()
				.map(group -> queryService.get(createOperation(
						batchOperationRequest(operationRequest, group, varietyGroups.size()),
						workType,
						new ResolvedSelection(
								resolvedSelection.selection(),
								resolvedSelection.resolved(),
								resolvedSelection.included().stream()
										.filter(target -> group.targetIds().contains(target.orchidGroupId()))
										.toList())).getId()))
				.toList();
	}

	public WorkOperationResponse createCompletedRecord(WorkOperationCreateRequest request) {
		workTypeService.getActiveForCreate(request.workTypeId());
		WorkType workType = workTypeService.getActiveForPlan(request.workTypeId());
		validatePeriodWorkType(workType);
		WorkOperation operation = createOperation(request, workType, resolveIncluded(request));
		LocalDateTime executedAt = support.completionTime(request.plannedStartDate());
		String worker = support.normalize(request.worker());
		operation.start(executedAt);
		List<WorkTargetExecution> executions = executionRepository
				.findByTargetWorkOperationIdOrderByIdAsc(operation.getId());
		for (WorkTargetExecution execution : executions) {
			var result = workEffectProcessor.apply(
					operation,
					execution.getTarget(),
					new WorkEffectCommand(executedAt, worker, request.details(), null));
			execution.completeWithEffect(executedAt, worker, result.resultDetails());
		}
		operation.complete(executedAt);
		return queryService.get(operation.getId());
	}

	private WorkOperation createOperation(
			WorkOperationCreateRequest request,
			WorkType workType,
			ResolvedSelection resolvedSelection) {
		support.validateDates(request.plannedStartDate(), request.plannedEndDate());
		validateSingleVariety(workType.getCode(), resolvedSelection.included());
		WorkOperation operation = new WorkOperation(
				workType,
				support.normalizeRequired(request.title()),
				request.plannedStartDate(),
				request.plannedEndDate(),
				request.sourceScopeType(),
				request.sourceScopeId(),
				conditionSnapshot(resolvedSelection.selection()),
				request.details(),
				support.normalize(request.worker()),
				support.normalize(request.memo()),
				support.now());
		return aggregateCreator.createForOrchidGroups(
				operation,
				resolvedSelection.included(),
				inclusionSource(request.sourceScopeType()),
				request.sourceScopeId());
	}

	private ResolvedSelection resolveIncluded(WorkOperationCreateRequest request) {
		WorkTargetSelection selection = selection(
				request.sourceScopeType(), request.sourceScopeId(), request.sourceScopeKey(),
				request.sourceOrchidGroupIds());
		List<ResolvedWorkTarget> resolved = workTargetResolver.resolve(selection);
		Set<Long> excludedIds = request.excludedOrchidGroupIds() == null
				? Set.of()
				: new HashSet<>(request.excludedOrchidGroupIds());
		Set<Long> resolvedIds = resolved.stream()
				.map(ResolvedWorkTarget::orchidGroupId)
				.collect(Collectors.toSet());
		if (!resolvedIds.containsAll(excludedIds)) {
			throw new IllegalArgumentException("제외 대상은 현재 해석된 난 묶음에 포함되어야 합니다.");
		}
		List<ResolvedWorkTarget> included = resolved.stream()
				.filter(group -> !excludedIds.contains(group.orchidGroupId()))
				.toList();
		if (included.isEmpty()) {
			throw new IllegalArgumentException("작업 대상 난 묶음이 한 개 이상 필요합니다.");
		}
		return new ResolvedSelection(selection, resolved, included);
	}

	private void validatePeriodWorkType(WorkType workType) {
		if (workType.effectKind() != WorkEffectKind.RECORD_ONLY
				&& !PERIOD_WORK_TYPES.contains(workType.getCode())) {
			throw new IllegalArgumentException("이 작업 유형은 난 묶음 대상 기간 작업으로 만들 수 없습니다.");
		}
	}

	private void validateSingleVariety(String workTypeCode, List<ResolvedWorkTarget> targets) {
		if (!requiresSingleVariety(workTypeCode)) {
			return;
		}
		Long varietyId = targets.getFirst().varietyId();
		if (varietyId == null || targets.stream().anyMatch(group -> !varietyId.equals(group.varietyId()))) {
			throw new IllegalArgumentException("분갈이·분주·합식 작업은 하나의 품종만 대상으로 계획할 수 있습니다.");
		}
	}

	private boolean requiresSingleVariety(String workTypeCode) {
		return SINGLE_VARIETY_WORK_TYPES.contains(workTypeCode);
	}

	private List<VarietyTargetGroup> groupTargetsByVariety(List<ResolvedWorkTarget> targets) {
		Map<String, VarietyTargetGroup> grouped = new LinkedHashMap<>();
		for (ResolvedWorkTarget target : targets) {
			String key = target.varietyId() == null ? "name:" + target.varietyName() : "id:" + target.varietyId();
			grouped.computeIfAbsent(key, ignored -> new VarietyTargetGroup(target.varietyName()))
					.targetIds().add(target.orchidGroupId());
		}
		return List.copyOf(grouped.values());
	}

	private WorkOperationCreateRequest batchOperationRequest(
			WorkOperationCreateRequest request,
			VarietyTargetGroup group,
			int varietyCount) {
		return new WorkOperationCreateRequest(
				request.workTypeId(),
				varietyTitle(request.title(), group.varietyName(), varietyCount),
				request.plannedStartDate(),
				request.plannedEndDate(),
				request.sourceScopeType(),
				request.sourceScopeId(),
				request.sourceScopeKey(),
				request.sourceOrchidGroupIds(),
				request.details(),
				request.worker(),
				request.memo(),
				List.of());
	}

	private String varietyTitle(String baseTitle, String varietyName, int varietyCount) {
		if (varietyCount <= 1 || varietyName == null || varietyName.isBlank()) {
			return support.normalizeRequired(baseTitle);
		}
		return support.normalizeRequired(baseTitle) + " - " + varietyName;
	}

	private WorkTargetSelection selection(
			com.greenhouse.backend.work.domain.operation.WorkSourceScopeType scopeType,
			Long scopeId,
			String scopeKey,
			List<Long> orchidGroupIds) {
		String normalizedKey = support.normalize(scopeKey);
		List<Long> normalizedIds = orchidGroupIds == null
				? List.of()
				: orchidGroupIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
		switch (scopeType) {
			case FARM -> {
				if (scopeId != null) throw new IllegalArgumentException("전체 농장 작업에는 대상 범위 ID를 지정할 수 없습니다.");
			}
			case HOUSE, PHYSICAL_BED, BED_ZONE, ORCHID_GROUP, USER_COLLECTION -> {
				if (scopeId == null) throw new IllegalArgumentException("선택한 작업 대상의 ID가 필요합니다.");
			}
			case DERIVED_GROUP -> {
				if (normalizedKey == null) throw new IllegalArgumentException("자동 그룹 키가 필요합니다.");
			}
			case MANUAL_SELECTION -> {
				if (normalizedIds.isEmpty()) throw new IllegalArgumentException("직접 선택한 난 묶음이 한 개 이상 필요합니다.");
			}
			default -> throw new IllegalArgumentException("아직 지원하지 않는 작업 대상 유형입니다.");
		}
		if (scopeType == com.greenhouse.backend.work.domain.operation.WorkSourceScopeType.ORCHID_GROUP) {
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

	private WorkTargetInclusionSource inclusionSource(
			com.greenhouse.backend.work.domain.operation.WorkSourceScopeType scopeType) {
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

	private record ResolvedSelection(
			WorkTargetSelection selection,
			List<ResolvedWorkTarget> resolved,
			List<ResolvedWorkTarget> included) {
	}

	private record VarietyTargetGroup(String varietyName, List<Long> targetIds) {
		private VarietyTargetGroup(String varietyName) {
			this(varietyName, new ArrayList<>());
		}
	}
}
