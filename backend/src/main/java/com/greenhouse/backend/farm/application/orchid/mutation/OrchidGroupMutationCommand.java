package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import java.time.LocalDate;

/** A new mutation command must declare its canonical fingerprint at compile time. */
public sealed interface OrchidGroupMutationCommand permits CreateOrchidGroupMutationCommand,
		CreateInboundOrchidGroupsMutationCommand,
		TransformOrchidGroupsMutationCommand, UpdateOrchidGroupMutationCommand, MoveOrchidGroupMutationCommand,
		MoveOrchidGroupsMutationCommand,
		CancelOrchidGroupCreationMutationCommand, DiscardOrchidGroupMutationCommand, ReserveOrchidGroupsMutationCommand,
		ReleaseOrchidGroupReservationsMutationCommand, ConsumeOrchidGroupReservationsMutationCommand,
		RestoreOutboundOrchidGroupsMutationCommand, CorrectOrchidGroupsMutationCommand,
		ReconcileOrchidGroupMutationCommand, CompensateTransformMutationsCommand, CompensateCreateMutationsCommand {

	OrchidGroupMutationSource source();

	LocalDate effectiveBusinessDate();

	String reason();

}
