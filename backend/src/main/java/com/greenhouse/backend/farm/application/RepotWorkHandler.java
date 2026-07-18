package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.farm.domain.OrchidGroupLineageRelationType;
import com.greenhouse.backend.work.application.effect.WorkEffectCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectHandler;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.domain.WorkEffectKind;
import com.greenhouse.backend.work.domain.WorkOperation;
import com.greenhouse.backend.work.domain.WorkOperationTarget;
import org.springframework.stereotype.Component;

@Component
public class RepotWorkHandler implements WorkEffectHandler {

	private final SingleSourceTransformationExecutor executor;
	private final BatchStructureTransformationExecutor batchExecutor;

	public RepotWorkHandler(
			SingleSourceTransformationExecutor executor,
			BatchStructureTransformationExecutor batchExecutor) {
		this.executor = executor;
		this.batchExecutor = batchExecutor;
	}

	@Override public String supports() { return "REPOT"; }
	@Override public WorkEffectKind effectKind() { return WorkEffectKind.STRUCTURE_CHANGE; }

	@Override
	public WorkExecutionResult execute(
			WorkOperation operation, WorkOperationTarget target, WorkEffectCommand command) {
		if (command.payload() instanceof com.greenhouse.backend.work.dto.StructureChangeExecutionRequest request) {
			return batchExecutor.execute(
					operation, request, "REPOT", "분갈이", OrchidGroupLineageRelationType.REPOTTED_TO);
		}
		return executor.execute(
				operation, target, command, "분갈이", "REPOT",
				OrchidGroupLineageRelationType.REPOTTED_TO);
	}
}
