package com.greenhouse.backend.farm.dto.transformation;

import com.greenhouse.backend.common.application.OrchidGroupUsage;
import java.util.List;

public record MultiCreateCancellationEligibilityResponse(
		Long workOperationId,
		boolean cancelable,
		List<Long> createdOrchidGroupIds,
		List<Blocker> blockers) {

	public record Blocker(String code, String message, long count) {
		public static Blocker from(OrchidGroupUsage usage) {
			return new Blocker(usage.code(), usage.message(), usage.count());
		}
	}
}
