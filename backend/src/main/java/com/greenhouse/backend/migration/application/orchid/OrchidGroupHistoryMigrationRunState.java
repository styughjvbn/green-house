package com.greenhouse.backend.migration.application.orchid;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record OrchidGroupHistoryMigrationRunState(
		Long id,
		UUID runKey,
		OrchidGroupHistoryMigrationRunPhase phase,
		Instant sourceCutoff,
		String sourceStateFingerprint,
		Map<String, Long> plannedCounts,
		Map<String, Long> importedCounts,
		Map<String, Object> verificationResult) {
}
