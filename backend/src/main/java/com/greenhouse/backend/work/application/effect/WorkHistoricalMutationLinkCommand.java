package com.greenhouse.backend.work.application.effect;

import java.util.UUID;

public record WorkHistoricalMutationLinkCommand(
		Long workEffectId,
		Long mutationId,
		UUID correlationId) {

	public WorkHistoricalMutationLinkCommand {
		if (workEffectId == null || mutationId == null || correlationId == null) {
			throw new IllegalArgumentException("Historical Work Mutation 연결 정보가 필요합니다.");
		}
	}
}
