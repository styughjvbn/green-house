package com.greenhouse.backend.work.application.effect;

import java.util.List;
import java.util.UUID;

public record WorkOrchidGroupLedgerRehearsalReport(List<Long> targetOrchidGroupIds, List<Long> effectOrchidGroupIds,
		List<Long> invalidExecutionIds, List<Long> incompleteMutationLinkEffectIds,
		List<CorrectionReference> corrections) {

	public WorkOrchidGroupLedgerRehearsalReport {
		targetOrchidGroupIds = List.copyOf(targetOrchidGroupIds);
		effectOrchidGroupIds = List.copyOf(effectOrchidGroupIds);
		invalidExecutionIds = List.copyOf(invalidExecutionIds);
		incompleteMutationLinkEffectIds = List.copyOf(incompleteMutationLinkEffectIds);
		corrections = List.copyOf(corrections);
	}

	public WorkOrchidGroupLedgerRehearsalReport(List<Long> targetOrchidGroupIds, List<Long> effectOrchidGroupIds,
			List<Long> invalidExecutionIds, List<Long> incompleteMutationLinkEffectIds) {
		this(targetOrchidGroupIds, effectOrchidGroupIds, invalidExecutionIds, incompleteMutationLinkEffectIds,
				List.of());
	}
	public record CorrectionReference(Long id, Long mutationId, UUID correlationId, boolean changesGroups,
			List<Long> orchidGroupIds) {
	}
}
