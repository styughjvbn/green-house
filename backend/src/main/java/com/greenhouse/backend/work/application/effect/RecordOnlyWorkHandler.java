package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.work.api.effect.WorkEffectCommand;
import com.greenhouse.backend.work.api.effect.WorkEffectContext;
import com.greenhouse.backend.work.api.effect.WorkEffectKind;
import com.greenhouse.backend.work.api.effect.WorkEffectResults;
import com.greenhouse.backend.work.api.effect.WorkExecutionResult;
import com.greenhouse.backend.work.spi.effect.WorkEffectHandler;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RecordOnlyWorkHandler implements WorkEffectHandler {

  public static final String CODE = "RECORD_ONLY";

  @Override
  public String supports() {
    return CODE;
  }

  @Override
  public WorkEffectKind effectKind() {
    return WorkEffectKind.RECORD_ONLY;
  }

  @Override
  public WorkExecutionResult execute(WorkEffectContext context, WorkEffectCommand command) {
    return new WorkExecutionResult(
        CODE, new WorkEffectResults.Json(command.resultDetails()), List.of());
  }
}
