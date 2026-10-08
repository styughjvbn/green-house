package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSource;
import java.time.LocalDate;

/** A new mutation command must declare its canonical fingerprint at compile time. */
public sealed interface OrchidGroupMutationCommand
    permits CreateOrchidGroupMutationCommand,
        CreateInboundOrchidGroupsMutationCommand,
        TransformOrchidGroupsMutationCommand,
        UpdateOrchidGroupMutationCommand,
        MoveOrchidGroupMutationCommand,
        MoveOrchidGroupsMutationCommand,
        CancelOrchidGroupCreationMutationCommand,
        DiscardOrchidGroupMutationCommand,
        ReserveOrchidGroupsMutationCommand,
        ReleaseOrchidGroupReservationsMutationCommand,
        ConsumeOrchidGroupReservationsMutationCommand,
        RestoreOutboundOrchidGroupsMutationCommand,
        CorrectOrchidGroupsMutationCommand,
        ReconcileOrchidGroupMutationCommand,
        StockCountOrchidGroupMutationCommand,
        CompensateTransformMutationsCommand,
        CompensateCreateMutationsCommand {

  OrchidGroupMutationSource source();

  LocalDate effectiveBusinessDate();

  String reason();
}
