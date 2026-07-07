package com.greenhouse.backend.farm.dto;

public record PlacementRecommendationAllocationResponse(
		Long segmentId,
		String segmentName,
		Integer quantity,
		Integer occupancyUnits,
		Integer remainingUnits) {
}
