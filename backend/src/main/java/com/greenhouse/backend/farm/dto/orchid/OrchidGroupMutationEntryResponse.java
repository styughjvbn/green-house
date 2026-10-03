package com.greenhouse.backend.farm.dto.orchid;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryKind;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;

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
