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
public class DivideWorkHandler implements WorkEffectHandler {

	private final SingleSourceTransformationExecutor executor;
	private final StructureChangeExecutor structureChangeExecutor;

	public DivideWorkHandler(
			SingleSourceTransformationExecutor executor,
			StructureChangeExecutor structureChangeExecutor) {
		this.executor = executor;
		this.structureChangeExecutor = structureChangeExecutor;
	}

	@Override public String supports() { return "DIVIDE"; }
	@Override public WorkEffectKind effectKind() { return WorkEffectKind.STRUCTURE_CHANGE; }

	@Override
	public WorkExecutionResult execute(
			WorkOperation operation, WorkOperationTarget target, WorkEffectCommand command) {
		if (command.payload() instanceof com.greenhouse.backend.work.dto.StructureChangeExecutionRequest request) {
			return structureChangeExecutor.execute(operation, request);
		}
		return executor.execute(
				operation, target, command, "분주", "DIVIDE",
				OrchidGroupLineageRelationType.SPLIT_TO);
	}
}
