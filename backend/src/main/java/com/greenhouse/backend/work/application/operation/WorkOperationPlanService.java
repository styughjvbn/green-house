package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectProcessor;
import com.greenhouse.backend.work.application.operation.WorkOperationView;
import com.greenhouse.backend.work.application.target.ResolvedWorkTarget;
import com.greenhouse.backend.work.application.target.WorkOperationTargetView;
import com.greenhouse.backend.work.application.target.WorkTargetResolver;
import com.greenhouse.backend.work.application.target.WorkTargetSelection;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import com.greenhouse.backend.work.dto.operation.WorkOperationBatchCreateRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationCreateRequest;
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

	private final WorkTargetResolver workTargetResolver;

	private final WorkTypeService workTypeService;

	private final WorkOperationAggregateCreator aggregateCreator;

	private final WorkTargetExecutionRepository executionRepository;

	private final WorkEffectProcessor workEffectProcessor;

	private final WorkOperationQueryService queryService;

	private final WorkOperationSupport support;

	@Transactional(readOnly = true)
	public WorkTargetPreviewResponse preview(WorkTargetPreviewRequest request) {
		WorkTargetSelection selection = WorkTargetSelection.from(request);
		List<ResolvedWorkTarget> groups = workTargetResolver.resolve(selection);
		var targets = groups.stream().map(WorkOperationTargetView::preview).toList();
		return new WorkTargetPreviewResponse(targets.size(),
				groups.stream().mapToInt(ResolvedWorkTarget::quantity).sum(), targets);
	}

	public WorkOperationView create(WorkOperationCreateRequest request) {
		WorkType workType = workTypeService.getActiveForPlan(request.workTypeId());
		return queryService.get(createOperation(request, workType, resolveIncluded(request)).getId());
	}

	public List<WorkOperationView> createBatch(WorkOperationBatchCreateRequest request) {
		WorkOperationCreateRequest operationRequest = request.operation();
		WorkType workType = workTypeService.getActiveForPlan(operationRequest.workTypeId());
		ResolvedSelection resolvedSelection = resolveIncluded(operationRequest);
		if (!workType.definition().supportsStructureExecution()) {
			return List.of(queryService.get(createOperation(operationRequest, workType, resolvedSelection).getId()));
		}
		List<VarietyTargetGroup> varietyGroups = groupTargetsByVariety(resolvedSelection.included());
		return varietyGroups.stream()
			.map(group -> queryService
				.get(createOperation(batchOperationRequest(operationRequest, group, varietyGroups.size()), workType,
						new ResolvedSelection(resolvedSelection.selection(), resolvedSelection.resolved(),
								resolvedSelection.included()
									.stream()
									.filter(target -> group.targetIds().contains(target.orchidGroupId()))
									.toList()))
					.getId()))
			.toList();
	}

	public WorkOperationView createCompletedRecord(WorkOperationCreateRequest request) {
		WorkType workType = workTypeService.getActiveForCreate(request.workTypeId());
		WorkOperation operation = createOperation(request, workType, resolveIncluded(request));
		LocalDateTime executedAt = support.completionTime(request.plannedStartDate());
		String worker = support.actor(request.worker());
		operation.start(executedAt);
		List<WorkTargetExecution> executions = executionRepository
			.findByTargetWorkOperationIdOrderByIdAsc(operation.getId());
		for (WorkTargetExecution execution : executions) {
			var result = workEffectProcessor.applyNew(operation, execution.getTarget(),
					new WorkEffectCommand(executedAt, worker, request.details(), null));
			execution.completeWithEffect(executedAt, worker, result.resultDetails());
		}
		operation.complete(executedAt);
		return queryService.get(operation.getId());
	}

	private WorkOperation createOperation(WorkOperationCreateRequest request, WorkType workType,
			ResolvedSelection resolvedSelection) {
		support.validateDates(request.plannedStartDate(), request.plannedEndDate());
		validateSingleVariety(workType.getCode(), resolvedSelection.included());
		WorkTargetSelection targetSelection = resolvedSelection.selection();
		WorkOperation operation = new WorkOperation(workType, support.normalizeRequired(request.title()),
				request.plannedStartDate(), request.plannedEndDate(), targetSelection.sourceScopeType(),
				targetSelection.sourceScopeId(), targetSelection.conditionSnapshot(), request.details(),
				support.actor(request.worker()), support.normalize(request.memo()), support.now());
		return aggregateCreator.createForOrchidGroups(operation, resolvedSelection.included(),
				targetSelection.inclusionSource(), targetSelection.sourceScopeId());
	}

	private ResolvedSelection resolveIncluded(WorkOperationCreateRequest request) {
		WorkTargetSelection selection = WorkTargetSelection.from(request);
		List<ResolvedWorkTarget> resolved = workTargetResolver.resolve(selection);
		Set<Long> excludedIds = request.excludedOrchidGroupIds() == null ? Set.of()
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
		return new ResolvedSelection(selection, resolved, included);
	}

	private void validateSingleVariety(String workTypeCode, List<ResolvedWorkTarget> targets) {
		if (!WorkTypeDefinition.forCode(workTypeCode).supportsStructureExecution()) {
			return;
		}
		Long varietyId = targets.getFirst().varietyId();
		if (varietyId == null || targets.stream().anyMatch(group -> !varietyId.equals(group.varietyId()))) {
			throw new IllegalArgumentException("자리 이동·분갈이·분주·합식 작업은 하나의 품종만 대상으로 계획할 수 있습니다.");
		}
	}

	private List<VarietyTargetGroup> groupTargetsByVariety(List<ResolvedWorkTarget> targets) {
		Map<String, VarietyTargetGroup> grouped = new LinkedHashMap<>();
		for (ResolvedWorkTarget target : targets) {
			String key = target.varietyId() == null ? "name:" + target.varietyName() : "id:" + target.varietyId();
			grouped.computeIfAbsent(key, ignored -> new VarietyTargetGroup(target.varietyName()))
				.targetIds()
				.add(target.orchidGroupId());
		}
		return List.copyOf(grouped.values());
	}

	private WorkOperationCreateRequest batchOperationRequest(WorkOperationCreateRequest request,
			VarietyTargetGroup group, int varietyCount) {
		return new WorkOperationCreateRequest(request.workTypeId(),
				varietyTitle(request.title(), group.varietyName(), varietyCount), request.plannedStartDate(),
				request.plannedEndDate(), WorkTargetSelection.from(request), request.details(), request.worker(),
				request.memo(), List.of());
	}

	private String varietyTitle(String baseTitle, String varietyName, int varietyCount) {
		if (varietyCount <= 1 || varietyName == null || varietyName.isBlank()) {
			return support.normalizeRequired(baseTitle);
		}
		return support.normalizeRequired(baseTitle) + " - " + varietyName;
	}

	private record ResolvedSelection(WorkTargetSelection selection, List<ResolvedWorkTarget> resolved,
			List<ResolvedWorkTarget> included) {
	}

	private record VarietyTargetGroup(String varietyName, List<Long> targetIds) {
		private VarietyTargetGroup(String varietyName) {
			this(varietyName, new ArrayList<>());
		}
	}

}
