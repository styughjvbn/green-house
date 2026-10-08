package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.work.api.effect.LegacyRepotCommand;
import com.greenhouse.backend.work.api.effect.StructureChangeCommand;
import com.greenhouse.backend.work.api.effect.WorkEffectCommand;
import com.greenhouse.backend.work.api.effect.WorkEffectContext;
import com.greenhouse.backend.work.api.effect.WorkEffectKind;
import com.greenhouse.backend.work.api.effect.WorkExecutionResult;
import com.greenhouse.backend.work.spi.effect.WorkEffectHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RepotWorkHandler implements WorkEffectHandler {

  private final StructureChangeExecutor structureChangeExecutor;

  private final LegacyStructureChangeRequestMapper legacyRequestMapper;

  private final OrchidGroupCollectionInheritanceService collectionInheritanceService;

  @Override
  public String supports() {
    return "REPOT";
  }

  @Override
  public WorkEffectKind effectKind() {
    return WorkEffectKind.STRUCTURE_CHANGE;
  }

  @Override
  public WorkExecutionResult execute(WorkEffectContext context, WorkEffectCommand command) {
    var target = context.target();
    if (command.payload() instanceof StructureChangeCommand request) {
      return structureChangeExecutor.execute(
          context, request, command.placementExclusionOrchidGroupIds());
    }
    if (target == null) throw new IllegalArgumentException("분갈이 작업에는 원본 난 묶음이 필요합니다.");
    LegacyRepotCommand request = legacyRequestMapper.read(command);
    if (!target.orchidGroupId().equals(request.sourceOrchidGroupId())) {
      throw new IllegalArgumentException("분갈이 작업 대상과 원본 난 묶음이 일치하지 않습니다.");
    }
    var collectionIds =
        collectionInheritanceService.validate(
            request.sourceOrchidGroupId(), request.inheritCollectionIds());
    var result = structureChangeExecutor.execute(context, legacyRequestMapper.from(request));
    collectionInheritanceService.inherit(
        collectionIds, result.resultOrchidGroupIds(), command.worker());
    return result;
  }
}
