package com.greenhouse.backend.farm.dto;

public record PlacementRequirementResponse(
	String placementType,
	String potSize,
	Integer quantity,
	Integer occupancyUnits,
	Boolean splitAllowed
) {
}
