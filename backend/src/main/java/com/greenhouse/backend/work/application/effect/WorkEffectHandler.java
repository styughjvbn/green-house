package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.work.domain.effect.WorkEffectKind;

public interface WorkEffectHandler {

	String supports();

	WorkEffectKind effectKind();

	WorkExecutionResult execute(WorkEffectContext context, WorkEffectCommand command);

}
