package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupStateSnapshot;
import java.math.BigDecimal;

public record OrchidGroupLedgerReconciliationGroup(
    Long orchidGroupId,
    Long stateRevision,
    OrchidGroupStateSnapshot snapshot,
    BigDecimal maximumPosition) {}
