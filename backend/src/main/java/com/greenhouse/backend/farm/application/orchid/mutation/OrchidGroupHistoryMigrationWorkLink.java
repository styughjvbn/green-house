package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import java.util.Set;

public record OrchidGroupHistoryMigrationWorkLink(
		Long workEffectId,
		Long workOperationId,
		OrchidGroupMutationSource source,
		Set<Long> sourceOrchidGroupIds,
		Set<Long> resultOrchidGroupIds) {

	public OrchidGroupHistoryMigrationWorkLink {
		sourceOrchidGroupIds = Set.copyOf(sourceOrchidGroupIds);
		resultOrchidGroupIds = Set.copyOf(resultOrchidGroupIds);
	}
}
