package com.greenhouse.backend.farm.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.farm.application.BedPlacementProfileService;
import com.greenhouse.backend.farm.dto.BedZonePlacementProfileRequest;
import com.greenhouse.backend.farm.dto.BedZonePlacementProfileResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bed-zones/{bedZoneId}/placement-profile")
public class BedPlacementController {
	private final BedPlacementProfileService service;

	public BedPlacementController(BedPlacementProfileService service) { this.service = service; }

	@GetMapping
	public ApiResponse<BedZonePlacementProfileResponse> get(@PathVariable Long bedZoneId) {
		return ApiResponse.ok(service.getProfile(bedZoneId));
	}

	@PutMapping
	public ApiResponse<BedZonePlacementProfileResponse> update(@PathVariable Long bedZoneId, @Valid @RequestBody BedZonePlacementProfileRequest request) {
		return ApiResponse.ok(service.updateProfile(bedZoneId, request));
	}
}
