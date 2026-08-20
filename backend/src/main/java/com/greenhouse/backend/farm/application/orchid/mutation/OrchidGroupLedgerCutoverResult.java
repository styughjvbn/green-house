package com.greenhouse.backend.farm.application.orchid.mutation;

import java.time.LocalDate;
import java.util.UUID;

public record OrchidGroupLedgerCutoverResult(
		UUID cutoverKey,
		LocalDate effectiveBusinessDate,
		String minimumWriterVersion,
		String currentWriterVersion,
		int baselineBatchCount,
		long baselineGroupCount,
		boolean activated,
		OrchidGroupLedgerReconciliationReport reconciliation) {
}
