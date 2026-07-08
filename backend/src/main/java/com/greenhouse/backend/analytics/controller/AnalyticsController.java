package com.greenhouse.backend.analytics.controller;

import com.greenhouse.backend.analytics.application.AnalyticsQueryService;
import com.greenhouse.backend.analytics.dto.PartnerAnalyticsResponse;
import com.greenhouse.backend.analytics.dto.SalesAnalyticsResponse;
import com.greenhouse.backend.analytics.dto.WorkAnalyticsResponse;
import com.greenhouse.backend.common.api.ApiResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

	private final AnalyticsQueryService analyticsQueryService;

	@GetMapping("/sales")
	public ApiResponse<SalesAnalyticsResponse> getSalesAnalytics(
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to) {
		return ApiResponse.ok(analyticsQueryService.getSalesAnalytics(from, to));
	}

	@GetMapping("/partners")
	public ApiResponse<PartnerAnalyticsResponse> getPartnerAnalytics(
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to) {
		return ApiResponse.ok(analyticsQueryService.getPartnerAnalytics(from, to));
	}

	@GetMapping("/work")
	public ApiResponse<WorkAnalyticsResponse> getWorkAnalytics(
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to) {
		return ApiResponse.ok(analyticsQueryService.getWorkAnalytics(from, to));
	}
}
