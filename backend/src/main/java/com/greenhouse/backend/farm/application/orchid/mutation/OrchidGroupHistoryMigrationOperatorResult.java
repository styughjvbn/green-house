package com.greenhouse.backend.farm.application.orchid.mutation;

import java.util.Map;
import java.util.UUID;

public record OrchidGroupHistoryMigrationOperatorResult(
		UUID runKey,
		Long runId,
		boolean applied,
		int batchCount,
		int importedMutations,
		int replayedMutations,
		int linkedWorkEffects,
		int linkedLineages,
		Map<String, Long> sourceCounts,
		Map<String, Long> plannedCounts,
		Map<String, Object> verification) {

	public OrchidGroupHistoryMigrationOperatorResult {
		sourceCounts = Map.copyOf(sourceCounts);
		plannedCounts = Map.copyOf(plannedCounts);
		verification = verification == null ? Map.of() : Map.copyOf(verification);
	}
}
