package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverageStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrchidGroupLedgerReconciliationReport(
		Instant inspectedAt,
		OrchidGroupLedgerReconciliationStage stage,
		UUID cutoverKey,
		OrchidGroupLedgerCoverageStatus coverageStatus,
		long orchidGroupCount,
		long revisionedGroupCount,
		long mutationCount,
		long entryCount,
		long baselineGroupCount,
		String baselineFingerprint,
		String currentStateFingerprint,
		boolean ready,
		List<OrchidGroupLedgerReconciliationIssue> issues) {
}
