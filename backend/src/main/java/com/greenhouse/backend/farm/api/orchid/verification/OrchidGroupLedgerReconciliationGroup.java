package com.greenhouse.backend.farm.api.orchid.verification;

import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupStateSnapshot;
import java.math.BigDecimal;

public record OrchidGroupLedgerReconciliationGroup(
    Long orchidGroupId,
    Long stateRevision,
    OrchidGroupStateSnapshot snapshot,
    BigDecimal maximumPosition) {}
