package com.greenhouse.backend.migration.application.orchid;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record OrchidGroupHistoryMigrationRunPlan(
		UUID runKey,
		Instant sourceCutoff,
		String backupFingerprint,
		String manifestFingerprint,
		String sourceStateFingerprint,
		LocalDate effectiveBusinessDate,
		Map<String, Long> sourceCounts,
		Map<String, Long> plannedCounts) {
}
