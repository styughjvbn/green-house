package com.greenhouse.backend.work.application.effect;

public record WorkStateChainMutationSource(
		Long workEffectId,
		Long workOperationId,
		String effectKey) {
}
