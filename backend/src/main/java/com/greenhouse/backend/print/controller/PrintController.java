package com.greenhouse.backend.print.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.print.application.PrintQueryService;
import com.greenhouse.backend.sales.dto.SalesSlipListItemResponse;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales-slips")
@RequiredArgsConstructor
public class PrintController {
	private final PrintQueryService printQueryService;

	@GetMapping("/print")
	public ApiResponse<PageResponse<SalesSlipListItemResponse>> getPrintableSalesSlips(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		return ApiResponse.ok(printQueryService.getPrintableSalesSlips(page, size));
	}

	@GetMapping("/{salesSlipId}/print")
	public ApiResponse<SalesSlipResponse> getSalesSlipPrintData(@PathVariable Long salesSlipId) {
		return ApiResponse.ok(printQueryService.getSalesSlipPrintData(salesSlipId));
	}
}
