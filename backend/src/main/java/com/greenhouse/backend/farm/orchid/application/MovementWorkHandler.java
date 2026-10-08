package com.greenhouse.backend.farm.orchid.application;

import com.greenhouse.backend.farm.application.transformation.StructureChangeExecutor;
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
public class MovementWorkHandler implements WorkEffectHandler {

  private final StructureChangeExecutor structureChangeExecutor;

  @Override
  public String supports() {
    return "MOVE";
  }

  @Override
  public WorkEffectKind effectKind() {
    return WorkEffectKind.ATTRIBUTE_CHANGE;
  }

  @Override
  public WorkExecutionResult execute(WorkEffectContext context, WorkEffectCommand command) {
    if (!(command.payload() instanceof StructureChangeCommand request)) {
      throw new IllegalArgumentException("자리 이동은 구조 변경 실행 또는 즉시 기록으로 처리해야 합니다.");
    }
    return structureChangeExecutor.execute(
        context, request, command.placementExclusionOrchidGroupIds());
  }
}
