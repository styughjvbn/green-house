package com.greenhouse.backend.farm.application.transformation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.farm.dto.transformation.RepotWorkOperationRequest;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.domain.effect.StructureChangeResultPurpose;
import com.greenhouse.backend.work.application.effect.StructureChangeCommand;
import com.greenhouse.backend.work.application.effect.StructureChangeResultInput;
import com.greenhouse.backend.work.application.effect.StructureChangeSourceInput;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LegacyStructureChangeRequestMapper {
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public RepotWorkOperationRequest read(WorkEffectCommand command) {
		return command.payload() == null
				? objectMapper.convertValue(command.resultDetails(), RepotWorkOperationRequest.class)
				: command.payloadAs(RepotWorkOperationRequest.class);
	}

	public StructureChangeCommand from(RepotWorkOperationRequest request) {
		Long sourceId = request.sourceOrchidGroupId();
		return new StructureChangeCommand(
				request.idempotencyKey(),
				request.workDate(),
				request.worker(),
				request.memo(),
				List.of(new StructureChangeSourceInput(sourceId, request.inputQuantity(), null, null)),
				request.results().stream()
						.map(result -> new StructureChangeResultInput(
								result.bedZoneId(),
								result.quantity(),
								sourceId,
								result.potSize(),
								result.ageYear(),
								StructureChangeResultPurpose.NORMAL,
								result.placementType(),
								result.trayCount(),
								result.splitPlacementAllowed(),
								result.startPosition(),
								result.endPosition(),
								result.memo()))
						.toList());
	}
}
