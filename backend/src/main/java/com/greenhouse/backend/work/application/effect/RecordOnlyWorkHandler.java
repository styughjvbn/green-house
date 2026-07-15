package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.work.domain.WorkEffectKind;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
import org.springframework.stereotype.Component;

@Component
public class RecordOnlyWorkHandler implements WorkEffectHandler {

	public static final String CODE = "RECORD_ONLY";

	@Override
	public WorkEffectKind supports() {
		return WorkEffectKind.RECORD_ONLY;
	}

	@Override
	public String handlerCode() {
		return CODE;
	}

	@Override
	public WorkExecutionResult execute(
			WorkOperation operation,
			WorkOperationTarget target,
			WorkEffectCommand command) {
		return new WorkExecutionResult(CODE, command.resultDetails());
	}
}
