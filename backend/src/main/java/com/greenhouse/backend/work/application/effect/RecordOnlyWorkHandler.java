package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
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
	public WorkExecutionResult execute(
			WorkOperation operation,
			WorkOperationTarget target,
			WorkEffectCommand command) {
		return new WorkExecutionResult(CODE, command.resultDetails(), List.of());
	}
}
