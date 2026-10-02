package com.greenhouse.backend.work.dto.operation;

import java.time.LocalDate;
import java.util.List;

public record WorkOperationCancellationEligibilityResponse(Long workOperationId, boolean cancellable,
		List<AffectedOperation> affectedOperations, List<AffectedOrchidGroup> affectedOrchidGroups,
		List<Blocker> blockers) {

	public record AffectedOperation(Long workOperationId, String title, String workTypeName, LocalDate workDate,
			boolean primary) {
	}

	public record AffectedOrchidGroup(Long orchidGroupId, String varietyName, Integer quantity, ImpactType impactType) {
	}

	public enum ImpactType {

		RECORD_CANCELED,

		RESTORED,

		CREATION_CANCELED

	}

	public record Blocker(String code, String message, long count) {
	}
}
