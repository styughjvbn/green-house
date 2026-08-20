package com.greenhouse.backend.work.application.operation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.work.dto.effect.InboundPottingExecutionRequest;
import com.greenhouse.backend.work.dto.effect.InboundPottingPlanBatchCreateRequest;
import com.greenhouse.backend.work.dto.effect.InboundPottingPlanCreateRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationResponse;
import com.greenhouse.backend.work.dto.target.WorkTargetExecutionRequest;
import com.greenhouse.backend.work.application.target.InboundPottingPlanGateway;
import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InboundPottingOperationService {

	private final InboundPottingPlanService planService;
	private final WorkOperationProgressService progressService;
	private final WorkOperationQueryService queryService;
	private final WorkTargetExecutionRepository workTargetExecutionRepository;
	private final WorkAppliedEffectRepository workAppliedEffectRepository;
	private final InboundPottingPlanGateway inboundPottingPlanGateway;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public InboundPottingOperationService(
			InboundPottingPlanService planService,
			WorkOperationProgressService progressService,
			WorkOperationQueryService queryService,
			WorkTargetExecutionRepository workTargetExecutionRepository,
			WorkAppliedEffectRepository workAppliedEffectRepository,
			InboundPottingPlanGateway inboundPottingPlanGateway) {
		this.planService = planService;
		this.progressService = progressService;
		this.queryService = queryService;
		this.workTargetExecutionRepository = workTargetExecutionRepository;
		this.workAppliedEffectRepository = workAppliedEffectRepository;
		this.inboundPottingPlanGateway = inboundPottingPlanGateway;
	}

	public WorkOperationResponse executeNow(InboundPottingExecutionRequest request) {
		inboundPottingPlanGateway.lockForPottingExecution(List.of(request.inboundRecordId()));
		WorkOperationResponse existing = findExisting(request);
		if (existing != null) {
			return existing;
		}
		return executeLocked(request);
	}

	public List<WorkOperationResponse> executeRecord(
			InboundPottingPlanCreateRequest plan,
			List<InboundPottingExecutionRequest> executions) {
		List<Long> inboundRecordIds = executions.stream()
				.map(InboundPottingExecutionRequest::inboundRecordId)
				.distinct()
				.sorted()
				.toList();
		inboundPottingPlanGateway.lockForPottingExecution(inboundRecordIds);

		Map<Long, WorkOperationResponse> existingByInboundRecordId = new LinkedHashMap<>();
		for (InboundPottingExecutionRequest execution : executions) {
			WorkOperationResponse existing = findExisting(execution);
			if (existing != null) {
				existingByInboundRecordId.put(execution.inboundRecordId(), existing);
			}
		}
		List<Long> pendingIds = inboundRecordIds.stream()
				.filter(id -> !existingByInboundRecordId.containsKey(id))
				.toList();
		if (!pendingIds.isEmpty()) {
			List<Long> activeIds = workTargetExecutionRepository
					.findActiveInboundPottingForUpdate(pendingIds)
					.stream()
					.map(item -> item.getTarget().getInboundRecordId())
					.distinct()
					.toList();
			List<Long> unplannedIds = pendingIds.stream()
					.filter(id -> !activeIds.contains(id))
					.toList();
			if (!unplannedIds.isEmpty()) {
				planService.createBatch(new InboundPottingPlanBatchCreateRequest(
						new InboundPottingPlanCreateRequest(
								plan.title(),
								plan.plannedStartDate(),
								plan.plannedEndDate(),
								unplannedIds,
								plan.worker(),
								plan.memo())));
			}
		}

		LinkedHashSet<Long> operationIds = new LinkedHashSet<>();
		executions.forEach(execution -> {
			WorkOperationResponse existing = existingByInboundRecordId.get(execution.inboundRecordId());
			WorkOperationResponse result = existing == null ? executeLocked(execution) : existing;
			operationIds.add(result.id());
		});
		return operationIds.stream().map(queryService::get).toList();
	}

	private WorkOperationResponse executeLocked(InboundPottingExecutionRequest request) {
		Long inboundRecordId = request.inboundRecordId();
		List<WorkTargetExecution> activeExecutions = workTargetExecutionRepository
				.findActiveInboundPottingForUpdate(inboundRecordId);
		if (!activeExecutions.isEmpty()) {
			return executeExisting(activeExecutions.getFirst(), request);
		}
		WorkOperationResponse planned = planService.create(
				new InboundPottingPlanCreateRequest(
						"입고 #" + inboundRecordId + " 포트 작업",
						request.pottingDate(),
						request.pottingDate(),
						List.of(inboundRecordId),
						request.worker(),
						request.memo()));
		WorkOperationResponse started = progressService.start(planned.id());
		Long targetId = started.targets().stream()
				.filter(target -> inboundRecordId.equals(target.inboundRecordId()))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("포트 작업 대상을 찾을 수 없습니다."))
				.id();
		return executeTarget(started, targetId, request);
	}

	private WorkOperationResponse findExisting(InboundPottingExecutionRequest request) {
		return workAppliedEffectRepository
				.findInboundPottingEffect(request.inboundRecordId(), effectKey(request))
				.map(effect -> existingResponse(effect, request))
				.orElse(null);
	}

	private WorkOperationResponse existingResponse(
			WorkAppliedEffect effect,
			InboundPottingExecutionRequest request) {
		if (!effect.getCommandDetails().equals(commandDetails(request))) {
			throw new IllegalArgumentException("같은 멱등 키를 다른 포트 작업 요청에 사용할 수 없습니다.");
		}
		return queryService.get(effect.getWorkOperation().getId());
	}

	private WorkOperationResponse executeExisting(
			WorkTargetExecution execution,
			InboundPottingExecutionRequest request) {
		Long operationId = execution.getTarget().getWorkOperation().getId();
		WorkOperationStatus status = execution.getTarget().getWorkOperation().getStatus();
		WorkOperationResponse active = switch (status) {
			case PLANNED -> progressService.start(operationId);
			case PAUSED -> progressService.resume(operationId);
			case IN_PROGRESS -> queryService.get(operationId);
			default -> throw new IllegalStateException("실행할 수 없는 포트 작업 계획입니다.");
		};
		return executeTarget(active, execution.getTarget().getId(), request);
	}

	private WorkOperationResponse executeTarget(
			WorkOperationResponse operation,
			Long targetId,
			InboundPottingExecutionRequest request) {
		Map<String, Object> resultDetails = commandDetails(request);
		WorkOperationResponse updated = progressService.completeTarget(
				operation.id(),
				targetId,
				new WorkTargetExecutionRequest(request.worker(), resultDetails, request.pottingDate()),
				request.idempotencyKey());
		if (updated.progress().pending() == 0
				&& updated.progress().inProgress() == 0
				&& updated.progress().partial() == 0
				&& updated.progress().failed() == 0) {
			return progressService.complete(updated.id(), request.pottingDate());
		}
		return updated;
	}

	private Map<String, Object> commandDetails(InboundPottingExecutionRequest request) {
		Map<String, Object> details = new LinkedHashMap<>(objectMapper.convertValue(
				request, new TypeReference<Map<String, Object>>() {}));
		details.remove("idempotencyKey");
		details.remove("inboundRecordId");
		return details;
	}

	private String effectKey(InboundPottingExecutionRequest request) {
		return "POTTING:" + request.idempotencyKey().trim();
	}
}
