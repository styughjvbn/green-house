package com.greenhouse.backend.farm.orchid.web.dto;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationEntryKind;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.mutation.ledger.domain.OrchidGroupMutationEntry;

public record OrchidGroupMutationEntryResponse(
    Long id,
    Long orchidGroupId,
    OrchidGroupMutationEntryKind entryKind,
    OrchidGroupMutationEntryRole role,
    Long stateRevisionBefore,
    Long stateRevisionAfter,
    OrchidGroupMutationStateResponse beforeState,
    OrchidGroupMutationStateResponse afterState) {

  public static OrchidGroupMutationEntryResponse from(OrchidGroupMutationEntry entry) {
    return new OrchidGroupMutationEntryResponse(
        entry.getId(),
        entry.getOrchidGroupId(),
        entry.getEntryKind(),
        entry.getRole(),
        entry.getStateRevisionBefore(),
        entry.getStateRevisionAfter(),
        OrchidGroupMutationStateResponse.from(entry.getBeforeState()),
        OrchidGroupMutationStateResponse.from(entry.getAfterState()));
  }
}
