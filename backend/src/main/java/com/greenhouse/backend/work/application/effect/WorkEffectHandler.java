package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;

public interface WorkEffectHandler {

	String supports();

	WorkEffectKind effectKind();

	WorkExecutionResult execute(
			WorkOperation operation,
			WorkOperationTarget target,
			WorkEffectCommand command);
}
