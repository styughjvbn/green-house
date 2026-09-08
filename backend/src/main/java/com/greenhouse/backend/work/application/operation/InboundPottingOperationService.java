package com.greenhouse.backend.work.application.operation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.work.application.effect.InboundPottingCommand;
import com.greenhouse.backend.work.application.operation.WorkOperationView;
import com.greenhouse.backend.work.application.target.InboundPottingPlanGateway;
import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import com.greenhouse.backend.work.dto.effect.InboundPottingPlanBatchCreateRequest;
import com.greenhouse.backend.work.dto.effect.InboundPottingPlanCreateRequest;
import com.greenhouse.backend.work.dto.target.WorkTargetExecutionRequest;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class InboundPottingOperationService {

	private final InboundPottingPlanService planService;

	private final WorkOperationProgressService progressService;

	private final WorkOperationQueryService queryService;

	private final WorkTargetExecutionRepository workTargetExecutionRepository;

	private final WorkAppliedEffectRepository workAppliedEffectRepository;

	private final InboundPottingPlanGateway inboundPottingPlanGateway;

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public WorkOperationView executeNow(InboundPottingCommand request) {
		Long inboundRecordId = request.inboundRecordId();
		inboundPottingPlanGateway.lockForPottingExecution(List.of(inboundRecordId));
		return findExistingOperationId(request).map(queryService::get).orElseGet(() -> executeActiveOrNewPlan(request));
	}

	public List<WorkOperationView> executeRecord(InboundPottingPlanCreateRequest plan,
			List<InboundPottingCommand> executions) {
		List<Long> inboundRecordIds = executions.stream()
			.map(InboundPottingCommand::inboundRecordId)
			.distinct()
			.sorted()
			.toList();
		inboundPottingPlanGateway.lockForPottingExecution(inboundRecordIds);

		Map<Long, Long> existingOperationIds = findExistingOperationIds(executions);
		List<Long> pendingIds = inboundRecordIds.stream().filter(id -> !existingOperationIds.containsKey(id)).toList();
		Map<Long, WorkTargetExecution> activeExecutions = prepareActiveExecutions(plan, pendingIds);

		LinkedHashSet<Long> operationIds = new LinkedHashSet<>();
		for (InboundPottingCommand request : executions) {
			Long existingOperationId = existingOperationIds.get(request.inboundRecordId());
			if (existingOperationId != null) {
				operationIds.add(existingOperationId);
				continue;
			}
			WorkTargetExecution activeExecution = activeExecutions.get(request.inboundRecordId());
			if (activeExecution == null) {
				throw new IllegalStateException("실행할 포트 작업 계획을 찾을 수 없습니다.");
			}
			operationIds.add(executeActivePlan(activeExecution, request).id());
		}
		return queryService.getAll(operationIds);
	}

	private WorkOperationView executeActiveOrNewPlan(InboundPottingCommand request) {
		Long inboundRecordId = request.inboundRecordId();
		List<WorkTargetExecution> activeExecutions = workTargetExecutionRepository
			.findActiveInboundPottingForUpdate(inboundRecordId);
		if (!activeExecutions.isEmpty()) {
			return executeActivePlan(activeExecutions.getFirst(), request);
		}
		return executeNewPlan(request);
	}

	private WorkOperationView executeNewPlan(InboundPottingCommand request) {
		Long inboundRecordId = request.inboundRecordId();
		WorkOperationView planned = planService
			.create(new InboundPottingPlanCreateRequest("입고 #" + inboundRecordId + " 포트 작업", request.pottingDate(),
					request.pottingDate(), List.of(inboundRecordId), request.worker(), request.memo()));
		WorkOperationView started = progressService.start(planned.id());
		Long targetId = started.targets()
			.stream()
			.filter(target -> inboundRecordId.equals(target.inboundRecordId()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("포트 작업 대상을 찾을 수 없습니다."))
			.id();
		return executeTarget(started, targetId, request);
	}

	private Optional<Long> findExistingOperationId(InboundPottingCommand request) {
		return workAppliedEffectRepository.findInboundPottingEffect(request.inboundRecordId(), effectKey(request))
			.map(effect -> validatedOperationId(effect, request));
	}

	private Map<Long, Long> findExistingOperationIds(List<InboundPottingCommand> requests) {
		if (requests.isEmpty()) {
			return Map.of();
		}
		List<WorkAppliedEffect> effects = workAppliedEffectRepository.findInboundPottingEffects(
				requests.stream().map(InboundPottingCommand::inboundRecordId).distinct().toList(),
				requests.stream().map(this::effectKey).distinct().toList());
		Map<Long, Long> operationIds = new LinkedHashMap<>();
		for (InboundPottingCommand request : requests) {
			effects.stream()
				.filter(effect -> matches(effect, request))
				.findFirst()
				.ifPresent(
						effect -> operationIds.put(request.inboundRecordId(), validatedOperationId(effect, request)));
		}
		return operationIds;
	}

	private boolean matches(WorkAppliedEffect effect, InboundPottingCommand request) {
		return request.inboundRecordId().equals(effect.getTarget().getInboundRecordId())
				&& effectKey(request).equals(effect.getEffectKey());
	}

	private Long validatedOperationId(WorkAppliedEffect effect, InboundPottingCommand request) {
		if (!effect.getCommandDetails().equals(commandDetails(request))) {
			throw new IllegalArgumentException("같은 멱등 키를 다른 포트 작업 요청에 사용할 수 없습니다.");
		}
		return effect.getWorkOperation().getId();
	}

	private Map<Long, WorkTargetExecution> prepareActiveExecutions(InboundPottingPlanCreateRequest plan,
			List<Long> inboundRecordIds) {
		if (inboundRecordIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, WorkTargetExecution> executionsByInboundRecordId = indexExecutions(
				workTargetExecutionRepository.findActiveInboundPottingForUpdate(inboundRecordIds));
		List<Long> unplannedIds = inboundRecordIds.stream()
			.filter(id -> !executionsByInboundRecordId.containsKey(id))
			.toList();
		if (!unplannedIds.isEmpty()) {
			planService.createBatch(new InboundPottingPlanBatchCreateRequest(copyPlan(plan, unplannedIds)));
			indexExecutions(workTargetExecutionRepository.findActiveInboundPottingForUpdate(unplannedIds))
				.forEach(executionsByInboundRecordId::putIfAbsent);
		}
		return executionsByInboundRecordId;
	}

	private Map<Long, WorkTargetExecution> indexExecutions(List<WorkTargetExecution> executions) {
		Map<Long, WorkTargetExecution> indexed = new LinkedHashMap<>();
		executions.forEach(execution -> indexed.putIfAbsent(execution.getTarget().getInboundRecordId(), execution));
		return indexed;
	}

	private InboundPottingPlanCreateRequest copyPlan(InboundPottingPlanCreateRequest plan,
			List<Long> inboundRecordIds) {
		return new InboundPottingPlanCreateRequest(plan.title(), plan.plannedStartDate(), plan.plannedEndDate(),
				inboundRecordIds, plan.worker(), plan.memo());
	}

	private WorkOperationView executeActivePlan(WorkTargetExecution execution, InboundPottingCommand request) {
		Long operationId = execution.getTarget().getWorkOperation().getId();
		WorkOperationStatus status = execution.getTarget().getWorkOperation().getStatus();
		WorkOperationView active = switch (status) {
			case PLANNED -> progressService.start(operationId);
			case PAUSED -> progressService.resume(operationId);
			case IN_PROGRESS -> queryService.get(operationId);
			default -> throw new IllegalStateException("실행할 수 없는 포트 작업 계획입니다.");
		};
		return executeTarget(active, execution.getTarget().getId(), request);
	}

	private WorkOperationView executeTarget(WorkOperationView operation, Long targetId, InboundPottingCommand request) {
		Map<String, Object> resultDetails = commandDetails(request);
		WorkOperationView updated = progressService.completeTarget(operation.id(), targetId,
				new WorkTargetExecutionRequest(request.worker(), resultDetails, request.pottingDate()),
				request.idempotencyKey());
		if (updated.progress().pending() == 0 && updated.progress().inProgress() == 0
				&& updated.progress().partial() == 0 && updated.progress().failed() == 0) {
			return progressService.complete(updated.id(), request.pottingDate());
		}
		return updated;
	}

	private Map<String, Object> commandDetails(InboundPottingCommand request) {
		Map<String, Object> details = new LinkedHashMap<>(
				objectMapper.convertValue(request, new TypeReference<Map<String, Object>>() {
				}));
		details.remove("idempotencyKey");
		details.remove("inboundRecordId");
		return details;
	}

	private String effectKey(InboundPottingCommand request) {
		return "POTTING:" + request.idempotencyKey().trim();
	}

}
