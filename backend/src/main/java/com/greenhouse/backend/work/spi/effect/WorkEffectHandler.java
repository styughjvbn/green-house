package com.greenhouse.backend.work.spi.effect;

import com.greenhouse.backend.work.api.effect.WorkEffectCommand;
import com.greenhouse.backend.work.api.effect.WorkEffectContext;
import com.greenhouse.backend.work.api.effect.WorkEffectKind;
import com.greenhouse.backend.work.api.effect.WorkExecutionResult;

public interface WorkEffectHandler {

  String supports();

  WorkEffectKind effectKind();

  WorkExecutionResult execute(WorkEffectContext context, WorkEffectCommand command);
}
