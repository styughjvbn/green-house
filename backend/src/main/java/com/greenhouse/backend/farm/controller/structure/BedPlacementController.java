package com.greenhouse.backend.farm.controller.structure;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.farm.application.structure.BedPlacementProfileService;
import com.greenhouse.backend.farm.dto.structure.BedZonePlacementProfileRequest;
import com.greenhouse.backend.farm.dto.structure.BedZonePlacementProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bed-zones/{bedZoneId}/placement-profile")
@RequiredArgsConstructor
public class BedPlacementController {
	private final BedPlacementProfileService service;

	@GetMapping
	public ApiResponse<BedZonePlacementProfileResponse> get(@PathVariable Long bedZoneId) {
		return ApiResponse.ok(service.getProfile(bedZoneId));
	}

	@PutMapping
	public ApiResponse<BedZonePlacementProfileResponse> update(@PathVariable Long bedZoneId,
			@Valid @RequestBody BedZonePlacementProfileRequest request) {
		return ApiResponse.ok(service.updateProfile(bedZoneId, request));
	}
}
