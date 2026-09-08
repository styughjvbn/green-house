package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.work.application.effect.StructureChangeCommand;
import com.greenhouse.backend.work.application.effect.WorkEffectContext;
import com.greenhouse.backend.work.application.effect.WorkExecutionResult;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StructureChangeExecutor {

	private final StructureChangeStrategyRegistry strategyRegistry;

	private final BatchStructureTransformationExecutor transformationExecutor;

	public WorkExecutionResult execute(WorkEffectContext context, StructureChangeCommand request) {
		return execute(context, request, Set.of());
	}

	public WorkExecutionResult execute(WorkEffectContext context, StructureChangeCommand request,
			Set<Long> placementExclusionOrchidGroupIds) {
		StructureChangeStrategy strategy = strategyRegistry.get(context.workTypeCode());
		strategy.validate(request);
		return transformationExecutor.execute(context.operationId(), request, strategy,
				placementExclusionOrchidGroupIds);
	}

}
