package com.greenhouse.backend.farm.mutation.ledger.repository;

import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationEntryKind;
import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupStateSnapshot;

public record ReconciliationEntryRow(
    Long orchidGroupId,
    OrchidGroupMutationEntryKind entryKind,
    Long stateRevisionBefore,
    Long stateRevisionAfter,
    OrchidGroupStateSnapshot beforeState,
    OrchidGroupStateSnapshot afterState,
    OrchidGroupMutationType mutationType,
    OrchidGroupMutationSourceDomain sourceDomain,
    String sourceReferenceId) {}
