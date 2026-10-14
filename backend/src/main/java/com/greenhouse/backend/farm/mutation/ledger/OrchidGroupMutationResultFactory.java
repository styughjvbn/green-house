package com.greenhouse.backend.farm.mutation.ledger;

import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationResult;
import com.greenhouse.backend.farm.mutation.ledger.domain.OrchidGroupMutation;
import com.greenhouse.backend.farm.mutation.ledger.domain.OrchidGroupMutationEntry;
import java.util.List;

public final class OrchidGroupMutationResultFactory {

  private OrchidGroupMutationResultFactory() {}

  public static OrchidGroupMutationResult from(
      OrchidGroupMutation mutation, List<OrchidGroupMutationEntry> entries) {
    return new OrchidGroupMutationResult(
        mutation.getId(),
        mutation.getMutationType(),
        mutation.getCorrelationId(),
        entries.stream().map(OrchidGroupMutationResultFactory::entryFrom).toList(),
        false);
  }

  public static OrchidGroupMutationResult asReplay(OrchidGroupMutationResult result) {
    return new OrchidGroupMutationResult(
        result.mutationId(), result.mutationType(), result.correlationId(), result.entries(), true);
  }

  private static OrchidGroupMutationResult.Entry entryFrom(OrchidGroupMutationEntry entry) {
    return new OrchidGroupMutationResult.Entry(
        entry.getOrchidGroupId(),
        entry.getEntryKind(),
        entry.getRole(),
        entry.getStateRevisionBefore(),
        entry.getStateRevisionAfter(),
        entry.getBeforeState(),
        entry.getAfterState());
  }
}
