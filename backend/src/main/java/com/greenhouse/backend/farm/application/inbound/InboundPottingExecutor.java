package com.greenhouse.backend.farm.application.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordPottingRequest;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectContext;
import com.greenhouse.backend.work.application.effect.WorkEffectHandler;
import com.greenhouse.backend.work.application.effect.WorkEffectResults;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.target.WorkTargetReferenceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InboundPottingExecutor implements WorkEffectHandler {

	private final InboundPottingService inboundPottingService;

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Override
	public String supports() {
		return "POTTING";
	}

	@Override
	public WorkEffectKind effectKind() {
		return WorkEffectKind.STRUCTURE_CHANGE;
	}

	@Override
	public WorkExecutionResult execute(WorkEffectContext context, WorkEffectCommand command) {
		var target = context.target();
		if (target == null || target.referenceType() != WorkTargetReferenceType.INBOUND_RECORD) {
			throw new IllegalArgumentException("포트 작업에는 입고 기록 대상이 필요합니다.");
		}
		InboundRecordPottingRequest request = objectMapper.convertValue(command.resultDetails(),
				InboundRecordPottingRequest.class);
		var result = inboundPottingService.potting(target.inboundRecordId(), request, context.operationId(),
				command.effectKey());
		var details = new WorkEffectResults.Potted(target.inboundRecordId(), result.createdOrchidGroupIds(),
				result.actualQuantity())
			.toMap();
		return new WorkExecutionResult("POTTING", details, result.createdOrchidGroupIds(), result.mutationLink());
	}

}
