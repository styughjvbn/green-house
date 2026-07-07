package com.greenhouse.backend.dashboard.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.dashboard.application.DashboardQueryService;
import com.greenhouse.backend.dashboard.dto.DashboardSummaryResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
	private final DashboardQueryService dashboardQueryService;

	@GetMapping("/summary")
	public ApiResponse<DashboardSummaryResponse> getSummary() {
		return ApiResponse.ok(dashboardQueryService.getSummary());
	}
}
