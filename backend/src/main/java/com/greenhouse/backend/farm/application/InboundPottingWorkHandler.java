package com.greenhouse.backend.farm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.farm.dto.InboundRecordPottingRequest;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectHandler;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.domain.WorkEffectKind;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
import com.greenhouse.backend.work.domain.WorkTargetReferenceType;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InboundPottingWorkHandler implements WorkEffectHandler {

	private final InboundRecordService inboundRecordService;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public InboundPottingWorkHandler(InboundRecordService inboundRecordService) {
		this.inboundRecordService = inboundRecordService;
	}

	@Override public String supports() { return "POTTING"; }
	@Override public WorkEffectKind effectKind() { return WorkEffectKind.STRUCTURE_CHANGE; }

	@Override
	public WorkExecutionResult execute(
			WorkOperation operation,
			WorkOperationTarget target,
			WorkEffectCommand command) {
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
		return new WorkExecutionResult(
				"POTTING",
				details,
				result.createdOrchidGroupIds());
	}
}
