package com.greenhouse.backend.work.application.effect;

import java.util.UUID;

public record WorkStateChainMutationLinkCommand(Long workEffectId, Long mutationId, UUID correlationId) {

	public WorkStateChainMutationLinkCommand {
		if (workEffectId == null || mutationId == null || correlationId == null) {
			throw new IllegalArgumentException("Work state-chain Mutation 연결 정보가 필요합니다.");
		}
	}
}
