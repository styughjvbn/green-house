package com.greenhouse.backend.farm.application.orchid.mutation;

import java.util.UUID;
import java.util.List;

public record OrchidGroupHistoryMigrationBatchResult(
		UUID runKey,
		int requestedMutations,
		int importedMutations,
		int replayedMutations,
		int evidenceCount,
		List<OrchidGroupHistoryMigrationImportedSource> importedSources) {

	public OrchidGroupHistoryMigrationBatchResult {
		importedSources = List.copyOf(importedSources);
	}
}
