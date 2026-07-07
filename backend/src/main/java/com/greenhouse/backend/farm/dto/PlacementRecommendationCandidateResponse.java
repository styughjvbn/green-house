package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.PlacementCapacityMode;
import com.greenhouse.backend.farm.domain.PlacementRecommendationStatus;
import java.util.List;

public record PlacementRecommendationCandidateResponse(
		Long bedZoneId,
		String bedZoneName,
		Long houseId,
		Integer houseNumber,
		Long physicalBedId,
		Integer physicalBedNumber,
		PlacementRecommendationStatus status,
		PlacementCapacityMode requiredMode,
		List<PlacementRecommendationAllocationResponse> allocations,
		List<String> warnings) {
}
