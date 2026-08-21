package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

public record OrchidGroupShadowPlan(
		OrchidGroupMutationSource source,
		OrchidGroupMutationType mutationType,
		String commandFingerprint,
		Map<String, Object> commandPayload,
		List<Entry> entries,
		String engineError) {

	public OrchidGroupShadowPlan {
		commandPayload = commandPayload == null
				? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(commandPayload));
		entries = entries == null ? List.of() : List.copyOf(entries);
	}

	public boolean acceptedByEnginePlan() {
		return engineError == null;
	}

	public record Entry(
			int ordinal,
			Long orchidGroupId,
			OrchidGroupMutationEntryRole role,
			OrchidGroupStateSnapshot beforeState,
			OrchidGroupStateSnapshot afterState) {
	}
}
