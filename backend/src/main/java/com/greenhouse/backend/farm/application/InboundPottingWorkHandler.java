package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectHandler;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.domain.WorkEffectKind;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
import org.springframework.stereotype.Component;

@Component
public class InboundPottingWorkHandler implements WorkEffectHandler {

	private final InboundPottingExecutor executor;

	public InboundPottingWorkHandler(InboundPottingExecutor executor) {
		this.executor = executor;
	}

	@Override public String supports() { return "POTTING"; }
	@Override public WorkEffectKind effectKind() { return WorkEffectKind.STRUCTURE_CHANGE; }

	@Override
	public WorkExecutionResult execute(
			WorkOperation operation,
			WorkOperationTarget target,
			WorkEffectCommand command) {
		return executor.execute(target, command);
	}
}
