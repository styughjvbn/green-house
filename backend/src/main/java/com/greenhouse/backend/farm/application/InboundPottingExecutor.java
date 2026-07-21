package com.greenhouse.backend.farm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.farm.dto.InboundRecordPottingRequest;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
import com.greenhouse.backend.work.domain.WorkTargetReferenceType;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Component;

@Component
public class InboundPottingExecutor {

	private final InboundRecordService inboundRecordService;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public InboundPottingExecutor(InboundRecordService inboundRecordService) {
		this.inboundRecordService = inboundRecordService;
	}

	public WorkExecutionResult execute(WorkOperationTarget target, WorkEffectCommand command) {
		if (target == null || target.getTargetReferenceType() != WorkTargetReferenceType.INBOUND_RECORD) {
			throw new IllegalArgumentException("포트 작업에는 입고 기록 대상이 필요합니다.");
		}
		InboundRecordPottingRequest request = objectMapper.convertValue(
				command.resultDetails(), InboundRecordPottingRequest.class);
		var result = inboundRecordService.pottingForOperation(target.getInboundRecordId(), request);
		var details = new LinkedHashMap<String, Object>();
		details.put("inboundRecordId", target.getInboundRecordId());
		details.put("createdOrchidGroupIds", result.createdOrchidGroupIds());
		details.put("actualQuantity", result.actualQuantity());
		details.put("resultCount", result.createdOrchidGroupIds().size());
		return new WorkExecutionResult("POTTING", details, result.createdOrchidGroupIds());
	}
}
