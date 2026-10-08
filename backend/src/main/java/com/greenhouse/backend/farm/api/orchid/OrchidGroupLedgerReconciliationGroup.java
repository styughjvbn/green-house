package com.greenhouse.backend.farm.api.orchid;

import java.math.BigDecimal;

public record OrchidGroupLedgerReconciliationGroup(
    Long orchidGroupId,
    Long stateRevision,
    OrchidGroupStateSnapshot snapshot,
    BigDecimal maximumPosition) {}
