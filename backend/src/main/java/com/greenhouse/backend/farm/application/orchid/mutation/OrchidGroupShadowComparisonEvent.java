package com.greenhouse.backend.farm.application.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupShadowComparisonStatus;
import java.util.List;
import java.util.Map;

record OrchidGroupShadowComparisonEvent(
		OrchidGroupMutationSource source,
		OrchidGroupMutationType mutationType,
		String commandFingerprint,
		OrchidGroupShadowComparisonStatus status,
		Map<String, Object> commandPayload,
		List<Map<String, Object>> expectedEntries,
		List<Map<String, Object>> actualEntries,
		List<Map<String, Object>> mismatches,
		String engineError) {
}
