package com.greenhouse.backend.farm.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.farm.application.FarmStatusService;
import com.greenhouse.backend.farm.domain.FarmStatusTargetType;
import com.greenhouse.backend.farm.domain.FarmZoomLevel;
import com.greenhouse.backend.farm.dto.FarmStatusMapResponse;
import com.greenhouse.backend.farm.dto.FarmStatusOrchidGroupListResponse;
import com.greenhouse.backend.farm.dto.FarmStatusZoomResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/farm-status")
public class FarmStatusController {

	private final FarmStatusService farmStatusService;

	public FarmStatusController(FarmStatusService farmStatusService) {
		this.farmStatusService = farmStatusService;
	}

	@GetMapping("/map")
	public ApiResponse<FarmStatusMapResponse> getMap() {
		return ApiResponse.ok(farmStatusService.getMap());
	}

	@GetMapping("/orchid-groups")
	public ApiResponse<FarmStatusOrchidGroupListResponse> getOrchidGroups(
		@RequestParam FarmStatusTargetType targetType,
		@RequestParam Long targetId
	) {
		return ApiResponse.ok(farmStatusService.getOrchidGroups(targetType, targetId));
	}

	@GetMapping("/zoom")
	public ApiResponse<FarmStatusZoomResponse> getZoom(
		@RequestParam FarmZoomLevel level,
		@RequestParam(required = false) Long houseId,
		@RequestParam(required = false) Long physicalBedId
	) {
		return ApiResponse.ok(farmStatusService.getZoom(level, houseId, physicalBedId));
	}
}
