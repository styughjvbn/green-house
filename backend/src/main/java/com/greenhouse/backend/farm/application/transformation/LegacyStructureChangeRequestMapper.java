package com.greenhouse.backend.farm.application.transformation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhouse.backend.farm.dto.transformation.RepotWorkOperationRequest;
import com.greenhouse.backend.work.api.effect.LegacyRepotCommand;
import com.greenhouse.backend.work.api.effect.StructureChangeCommand;
import com.greenhouse.backend.work.api.effect.StructureChangeResultInput;
import com.greenhouse.backend.work.api.effect.StructureChangeResultPurpose;
import com.greenhouse.backend.work.api.effect.StructureChangeSourceInput;
import com.greenhouse.backend.work.api.effect.WorkEffectCommand;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LegacyStructureChangeRequestMapper {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  public LegacyRepotCommand read(WorkEffectCommand command) {
    return command.payload() == null
        ? objectMapper.convertValue(command.resultDetails(), LegacyRepotCommand.class)
        : command.payloadAs(LegacyRepotCommand.class);
  }

  public LegacyRepotCommand fromRequest(RepotWorkOperationRequest request) {
    return new LegacyRepotCommand(
        request.idempotencyKey(),
        request.title(),
        request.workDate(),
        request.worker(),
        request.memo(),
        request.sourceOrchidGroupId(),
        request.inputQuantity(),
        request.results().stream()
            .map(
                result ->
                    new LegacyRepotCommand.Result(
                        result.bedZoneId(),
                        result.quantity(),
                        result.potSize(),
                        result.ageYear(),
                        result.placementType(),
                        result.trayCount(),
                        result.splitPlacementAllowed(),
                        result.startPosition(),
                        result.endPosition(),
                        result.memo()))
            .toList(),
        request.inheritCollectionIds());
  }

  Merge readMerge(Map<String, Object> details) {
    return objectMapper.convertValue(details, Merge.class);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Merge(List<MergeSource> sources, LegacyRepotCommand.Result result) {}

  record MergeSource(Long sourceOrchidGroupId, Integer inputQuantity) {}

  public StructureChangeCommand from(LegacyRepotCommand request) {
    Long sourceId = request.sourceOrchidGroupId();
    return new StructureChangeCommand(
        request.idempotencyKey(),
        request.workDate(),
        request.worker(),
        request.memo(),
        List.of(new StructureChangeSourceInput(sourceId, request.inputQuantity(), null, null)),
        request.results().stream()
            .map(
                result ->
                    new StructureChangeResultInput(
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
