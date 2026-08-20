package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntry;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryKind;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import java.util.List;
import java.util.UUID;

public record OrchidGroupMutationResult(
		Long mutationId,
		OrchidGroupMutationType mutationType,
		UUID correlationId,
		List<Entry> entries) {

	public static OrchidGroupMutationResult from(
			OrchidGroupMutation mutation,
			List<OrchidGroupMutationEntry> entries) {
		return new OrchidGroupMutationResult(
				mutation.getId(),
				mutation.getMutationType(),
				mutation.getCorrelationId(),
				entries.stream().map(Entry::from).toList());
	}

	public record Entry(
			Long orchidGroupId,
			OrchidGroupMutationEntryKind entryKind,
			OrchidGroupMutationEntryRole role,
			Long stateRevisionBefore,
			Long stateRevisionAfter,
			OrchidGroupStateSnapshot beforeState,
			OrchidGroupStateSnapshot afterState) {

		private static Entry from(OrchidGroupMutationEntry entry) {
			return new Entry(
					entry.getOrchidGroupId(),
					entry.getEntryKind(),
					entry.getRole(),
					entry.getStateRevisionBefore(),
					entry.getStateRevisionAfter(),
					entry.getBeforeState(),
					entry.getAfterState());
		}
	}
}
