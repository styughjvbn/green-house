package com.greenhouse.backend.farm.mutation.verification;

import com.greenhouse.backend.farm.api.orchid.verification.OrchidGroupLedgerReconciliationIssue;
import com.greenhouse.backend.farm.mutation.ledger.domain.OrchidGroupLedgerCoverageStatus;
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
    List<OrchidGroupLedgerReconciliationIssue> issues) {}
