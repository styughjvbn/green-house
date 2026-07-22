package com.greenhouse.backend.farm.dto.structure;

public record PlacementRequirementResponse(
		String placementType,
		String potSize,
		Integer quantity,
		Integer occupancyUnits,
		Boolean splitAllowed) {
}
