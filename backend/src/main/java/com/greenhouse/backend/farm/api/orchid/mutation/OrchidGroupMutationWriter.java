package com.greenhouse.backend.farm.api.orchid.mutation;

/** Applies external mutations within the caller's transaction. */
public interface OrchidGroupMutationWriter {

  OrchidGroupMutationResult create(CreateOrchidGroupMutationCommand command);

  OrchidGroupMutationResult reserve(ReserveOrchidGroupsMutationCommand command);

  OrchidGroupMutationResult releaseReservation(
      ReleaseOrchidGroupReservationsMutationCommand command);

  OrchidGroupMutationResult consumeReservation(
      ConsumeOrchidGroupReservationsMutationCommand command);

  OrchidGroupMutationResult restoreOutbound(RestoreOutboundOrchidGroupsMutationCommand command);

  OrchidGroupMutationResult compensateCreations(CompensateCreateMutationsCommand command);
}
