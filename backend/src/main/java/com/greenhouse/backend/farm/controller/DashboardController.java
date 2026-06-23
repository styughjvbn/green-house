package com.greenhouse.backend.farm.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.farm.application.FarmStatusService;
import com.greenhouse.backend.farm.dto.DashboardSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

	private final FarmStatusService farmStatusService;

	public DashboardController(FarmStatusService farmStatusService) {
		this.farmStatusService = farmStatusService;
	}

	@GetMapping("/summary")
	public ApiResponse<DashboardSummaryResponse> getSummary() {
		return ApiResponse.ok(farmStatusService.getDashboardSummary());
	}
}
