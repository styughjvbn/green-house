package com.greenhouse.backend.work.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.work.dto.InboundPottingExecutionRequest;
import com.greenhouse.backend.work.dto.InboundPottingPlanCreateRequest;
import com.greenhouse.backend.work.dto.WorkOperationResponse;
import com.greenhouse.backend.work.dto.WorkTargetExecutionRequest;
import com.greenhouse.backend.work.domain.WorkOperationStatus;
import com.greenhouse.backend.work.domain.WorkTargetExecution;
import com.greenhouse.backend.work.repository.WorkTargetExecutionRepository;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InboundPottingOperationService {

	private final WorkOperationService workOperationService;
	private final WorkTargetExecutionRepository workTargetExecutionRepository;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public InboundPottingOperationService(
			WorkOperationService workOperationService,
			WorkTargetExecutionRepository workTargetExecutionRepository) {
		this.workOperationService = workOperationService;
		this.workTargetExecutionRepository = workTargetExecutionRepository;
	}

	public WorkOperationResponse executeNow(InboundPottingExecutionRequest request) {
		Long inboundRecordId = request.inboundRecordId();
		List<WorkTargetExecution> activeExecutions = workTargetExecutionRepository
				.findActiveInboundPottingForUpdate(inboundRecordId);
		if (!activeExecutions.isEmpty()) {
			return executeExisting(activeExecutions.getFirst(), request);
		}
		WorkOperationResponse planned = workOperationService.createInboundPottingPlan(
				new InboundPottingPlanCreateRequest(
						"입고 #" + inboundRecordId + " 포트 작업",
						request.pottingDate(),
						request.pottingDate(),
						List.of(inboundRecordId),
						request.worker(),
						request.memo()));
		WorkOperationResponse started = workOperationService.start(planned.id());
		Long targetId = started.targets().stream()
				.filter(target -> inboundRecordId.equals(target.inboundRecordId()))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("포트 작업 대상을 찾을 수 없습니다."))
				.id();
		return executeTarget(started, targetId, request);
	}

	private WorkOperationResponse executeExisting(
			WorkTargetExecution execution,
			InboundPottingExecutionRequest request) {
		Long operationId = execution.getTarget().getWorkOperation().getId();
		WorkOperationStatus status = execution.getTarget().getWorkOperation().getStatus();
		WorkOperationResponse active = switch (status) {
			case PLANNED -> workOperationService.start(operationId);
			case PAUSED -> workOperationService.resume(operationId);
			case IN_PROGRESS -> workOperationService.get(operationId);
			default -> throw new IllegalStateException("실행할 수 없는 포트 작업 계획입니다.");
		};
		return executeTarget(active, execution.getTarget().getId(), request);
	}

	private WorkOperationResponse executeTarget(
			WorkOperationResponse operation,
			Long targetId,
			InboundPottingExecutionRequest request) {
		Map<String, Object> resultDetails = new LinkedHashMap<>(objectMapper.convertValue(
				request, new TypeReference<Map<String, Object>>() {}));
		resultDetails.remove("inboundRecordId");
		WorkOperationResponse updated = workOperationService.completeTarget(
				operation.id(),
				targetId,
				new WorkTargetExecutionRequest(request.worker(), resultDetails));
		if (updated.progress().pending() == 0
				&& updated.progress().inProgress() == 0
				&& updated.progress().partial() == 0
				&& updated.progress().failed() == 0) {
			return workOperationService.complete(updated.id());
		}
		return updated;
	}
}
