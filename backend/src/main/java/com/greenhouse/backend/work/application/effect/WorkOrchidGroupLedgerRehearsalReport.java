package com.greenhouse.backend.work.application.effect;

import java.util.List;

public record WorkOrchidGroupLedgerRehearsalReport(List<Long> targetOrchidGroupIds, List<Long> effectOrchidGroupIds,
		List<Long> invalidExecutionIds, List<Long> incompleteMutationLinkEffectIds) {

	public WorkOrchidGroupLedgerRehearsalReport {
		targetOrchidGroupIds = List.copyOf(targetOrchidGroupIds);
		effectOrchidGroupIds = List.copyOf(effectOrchidGroupIds);
		invalidExecutionIds = List.copyOf(invalidExecutionIds);
		incompleteMutationLinkEffectIds = List.copyOf(incompleteMutationLinkEffectIds);
	}
}
