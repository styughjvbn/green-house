package com.greenhouse.backend.farm.application.orchid.mutation;

import java.util.List;
import java.util.Map;

public record OrchidGroupHistoryMigrationPlan(
		List<OrchidGroupHistoricalMutationInput> mutations,
		List<OrchidGroupHistoryMigrationWorkLink> workLinks,
		Map<String, Long> sourceCounts,
		Map<String, Long> plannedCounts) {

	public OrchidGroupHistoryMigrationPlan {
		mutations = List.copyOf(mutations);
		workLinks = List.copyOf(workLinks);
		sourceCounts = Map.copyOf(sourceCounts);
		plannedCounts = Map.copyOf(plannedCounts);
	}
}
