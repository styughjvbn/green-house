package com.greenhouse.backend.work.dto.operation;

import java.util.List;

public record WorkOperationVoidEligibilityResponse(Long workOperationId, boolean voidable, List<Long> mutationIds,
		List<Long> sourceOrchidGroupIds, List<Long> resultOrchidGroupIds, List<Long> relatedWorkOperationIds,
		List<Blocker> blockers) {

	public record Blocker(String code, String message, long count) {
	}
}
