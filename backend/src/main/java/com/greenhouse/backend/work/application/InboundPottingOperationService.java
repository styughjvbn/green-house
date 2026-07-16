package com.greenhouse.backend.work.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.work.dto.InboundPottingExecutionRequest;
import com.greenhouse.backend.work.dto.InboundPottingPlanCreateRequest;
import com.greenhouse.backend.work.dto.WorkOperationResponse;
import com.greenhouse.backend.work.dto.WorkTargetExecutionRequest;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InboundPottingOperationService {

	private final WorkOperationService workOperationService;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public InboundPottingOperationService(WorkOperationService workOperationService) {
		this.workOperationService = workOperationService;
	}

	public WorkOperationResponse executeNow(InboundPottingExecutionRequest request) {
		Long inboundRecordId = request.inboundRecordId();
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
		Map<String, Object> resultDetails = new LinkedHashMap<>(objectMapper.convertValue(
				request, new TypeReference<Map<String, Object>>() {}));
		resultDetails.remove("inboundRecordId");
		workOperationService.completeTarget(
				planned.id(),
				targetId,
				new WorkTargetExecutionRequest(request.worker(), resultDetails));
		return workOperationService.complete(planned.id());
	}
}
