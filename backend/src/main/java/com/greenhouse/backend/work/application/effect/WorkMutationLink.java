package com.greenhouse.backend.work.application.effect;

import java.util.UUID;

public record WorkMutationLink(Long mutationId, UUID correlationId) {

	public WorkMutationLink {
		if (mutationId == null || correlationId == null) {
			throw new IllegalArgumentException("작업 효과에 연결할 Mutation 정보가 필요합니다.");
		}
	}
}
