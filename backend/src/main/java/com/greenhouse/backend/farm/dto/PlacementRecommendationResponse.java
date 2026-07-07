package com.greenhouse.backend.farm.dto;

import java.util.List;

public record PlacementRecommendationResponse(
		Long orchidGroupId,
		String varietyName,
		PlacementRequirementResponse requirement,
		List<PlacementRecommendationCandidateResponse> candidates) {
}
