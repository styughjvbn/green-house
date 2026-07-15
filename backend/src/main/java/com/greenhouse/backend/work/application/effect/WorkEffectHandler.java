package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.work.domain.WorkEffectKind;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationTarget;

public interface WorkEffectHandler {

	WorkEffectKind supports();

	String handlerCode();

	WorkExecutionResult execute(
			WorkOperation operation,
			WorkOperationTarget target,
			WorkEffectCommand command);
}
