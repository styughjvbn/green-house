package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.work.domain.WorkEffectKind;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
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
