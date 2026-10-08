package com.greenhouse.backend.farm.repository.orchid.mutation;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationEntryKind;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationType;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupStateSnapshot;

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
