package com.greenhouse.backend.farm.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.farm.application.FarmQueryService;
import com.greenhouse.backend.farm.application.PlacementRecommendationService;
import com.greenhouse.backend.farm.dto.OrchidGroupResponse;
import com.greenhouse.backend.farm.dto.PlacementRecommendationResponse;

import lombok.RequiredArgsConstructor;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orchid-groups")
@RequiredArgsConstructor
public class OrchidGroupQueryController {
	private final FarmQueryService farmQueryService;
	private final PlacementRecommendationService placementRecommendationService;

	@GetMapping
	public ApiResponse<List<OrchidGroupResponse>> getOrchidGroups(
			@RequestParam(required = false) Long houseId,
			@RequestParam(required = false) Long physicalBedId,
			@RequestParam(required = false) Long bedZoneId,
			@RequestParam(required = false) String status) {
		return ApiResponse.ok(farmQueryService.getOrchidGroups(houseId, physicalBedId, bedZoneId, status));
	}

	@GetMapping("/{orchidGroupId}/placement-recommendations")
	public ApiResponse<PlacementRecommendationResponse> getPlacementRecommendations(
			@PathVariable Long orchidGroupId,
			@RequestParam(required = false) Long houseId) {
		return ApiResponse.ok(placementRecommendationService.recommend(orchidGroupId, houseId));
	}
}
