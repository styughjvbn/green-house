package com.greenhouse.backend.farm.application.orchid.mutation;

import java.util.UUID;

public record OrchidGroupStateChainMigrationResult(
		UUID cutoverKey,
		boolean applied,
		int mutationCount,
		int entryCount,
		int currentGroupCount,
		int deletedGroupCount,
		int importedMutationCount,
		int replayedMutationCount,
		OrchidGroupLedgerReconciliationReport reconciliation) {
}
