package com.greenhouse.backend.farm.application.orchid;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupMoveRequest;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectHandler;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import com.greenhouse.backend.work.domain.target.WorkTargetReferenceType;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MovementWorkHandler implements WorkEffectHandler {

	private final OrchidGroupCommandService orchidGroupCommandService;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public MovementWorkHandler(OrchidGroupCommandService orchidGroupCommandService) {
		this.orchidGroupCommandService = orchidGroupCommandService;
	}

	@Override public String supports() { return "MOVE"; }
	@Override public WorkEffectKind effectKind() { return WorkEffectKind.ATTRIBUTE_CHANGE; }

	@Override
	public WorkExecutionResult execute(
			WorkOperation operation,
			WorkOperationTarget target,
			WorkEffectCommand command) {
		if (target == null || target.getTargetReferenceType() != WorkTargetReferenceType.ORCHID_GROUP) {
			throw new IllegalArgumentException("자리 이동 작업에는 난 묶음 대상이 필요합니다.");
		}
		OrchidGroupMoveRequest request = command.payload() == null
				? objectMapper.convertValue(command.resultDetails(), OrchidGroupMoveRequest.class)
				: command.payloadAs(OrchidGroupMoveRequest.class);
		var moved = orchidGroupCommandService.moveForOperation(target.getOrchidGroupId(), request);
		var details = new LinkedHashMap<String, Object>();
		details.put("orchidGroupId", target.getOrchidGroupId());
		details.put("fromBedZoneId", target.getLocationSnapshot().get("bedZoneId"));
		details.put("toBedZoneId", moved.bedZoneId());
		details.put("startPosition", moved.startPosition());
		details.put("endPosition", moved.endPosition());
		return new WorkExecutionResult("MOVE", details, List.of(target.getOrchidGroupId()));
	}
}
