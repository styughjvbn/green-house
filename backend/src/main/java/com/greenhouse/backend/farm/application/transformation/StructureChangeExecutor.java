package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.dto.effect.StructureChangeExecutionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StructureChangeExecutor {

	private final StructureChangeStrategyRegistry strategyRegistry;
	private final BatchStructureTransformationExecutor transformationExecutor;

	public WorkExecutionResult execute(
			WorkOperation operation,
			StructureChangeExecutionRequest request) {
		StructureChangeStrategy strategy = strategyRegistry.get(operation.getWorkType().getCode());
		strategy.validate(request);
		return transformationExecutor.execute(
				operation,
				request,
				strategy.supports(),
				strategy.workLabel(),
				strategy.lineageType());
	}
}
