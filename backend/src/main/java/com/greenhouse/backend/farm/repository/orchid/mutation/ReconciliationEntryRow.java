package com.greenhouse.backend.farm.repository.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryKind;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;

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
