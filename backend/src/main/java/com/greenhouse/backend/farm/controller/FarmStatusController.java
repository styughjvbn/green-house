package com.greenhouse.backend.farm.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.farm.application.FarmStatusService;
import com.greenhouse.backend.farm.domain.FarmStatusTargetType;
import com.greenhouse.backend.farm.domain.FarmZoomLevel;
import com.greenhouse.backend.farm.dto.FarmStatusMapResponse;
import com.greenhouse.backend.farm.dto.FarmStatusOrchidGroupListResponse;
import com.greenhouse.backend.farm.dto.FarmStatusZoomResponse;
import com.greenhouse.backend.farm.dto.OrchidManagementViewportResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/farm-status")
@RequiredArgsConstructor
public class FarmStatusController {

	private final FarmStatusService farmStatusService;

	@GetMapping("/map")
	public ApiResponse<FarmStatusMapResponse> getMap() {
		return ApiResponse.ok(farmStatusService.getMap());
	}

	@GetMapping("/orchid-groups")
	public ApiResponse<FarmStatusOrchidGroupListResponse> getOrchidGroups(
			@RequestParam FarmStatusTargetType targetType,
			@RequestParam Long targetId) {
		return ApiResponse.ok(farmStatusService.getOrchidGroups(targetType, targetId));
	}

	@GetMapping("/orchid-management")
	public ApiResponse<OrchidManagementViewportResponse> getOrchidManagementViewport(
			@RequestParam(required = false) Long startBedId,
			@RequestParam(defaultValue = "3") int bedCount) {
		return ApiResponse.ok(farmStatusService.getOrchidManagementViewport(startBedId, bedCount));
	}

	@GetMapping("/zoom")
	public ApiResponse<FarmStatusZoomResponse> getZoom(
			@RequestParam FarmZoomLevel level,
			@RequestParam(required = false) Long houseId,
			@RequestParam(required = false) Long physicalBedId) {
		return ApiResponse.ok(farmStatusService.getZoom(level, houseId, physicalBedId));
	}
}
