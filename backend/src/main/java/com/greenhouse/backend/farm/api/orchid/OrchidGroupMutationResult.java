package com.greenhouse.backend.farm.api.orchid;

import java.util.List;
import java.util.UUID;

public record OrchidGroupMutationResult(
    Long mutationId,
    OrchidGroupMutationType mutationType,
    UUID correlationId,
    List<Entry> entries,
    boolean replayed) {

  public record Entry(
      Long orchidGroupId,
      OrchidGroupMutationEntryKind entryKind,
      OrchidGroupMutationEntryRole role,
      Long stateRevisionBefore,
      Long stateRevisionAfter,
      OrchidGroupStateSnapshot beforeState,
      OrchidGroupStateSnapshot afterState) {}
}
