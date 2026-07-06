package com.greenhouse.backend.sales.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.sales.application.SalesQueryService;
import com.greenhouse.backend.sales.application.SalesSlipCreationService;
import com.greenhouse.backend.sales.dto.AuctionShipmentOptionResponse;
import com.greenhouse.backend.sales.dto.SalesSlipCreateRequest;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SalesController {

	private final SalesQueryService salesQueryService;
	private final SalesSlipCreationService salesSlipCreationService;

	public SalesController(
		SalesQueryService salesQueryService,
		SalesSlipCreationService salesSlipCreationService
	) {
		this.salesQueryService = salesQueryService;
		this.salesSlipCreationService = salesSlipCreationService;
	}

	@GetMapping("/sales-slips")
	public ApiResponse<List<SalesSlipResponse>> getSalesSlips(
		@RequestParam(required = false) Long partnerId,
		@RequestParam(required = false) LocalDate from,
		@RequestParam(required = false) LocalDate to
	) {
		return ApiResponse.ok(salesQueryService.getSalesSlips(partnerId, from, to));
	}

	@GetMapping("/sales-slips/{salesSlipId}")
	public ApiResponse<SalesSlipResponse> getSalesSlip(@PathVariable Long salesSlipId) {
		return ApiResponse.ok(salesQueryService.getSalesSlip(salesSlipId));
	}

	@GetMapping("/sales-slips/auction-shipments")
	public ApiResponse<List<AuctionShipmentOptionResponse>> getAuctionShipmentOptions() {
		return ApiResponse.ok(salesQueryService.getAuctionShipmentOptions());
	}

	@PostMapping("/sales-slips")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<SalesSlipResponse> createSalesSlip(@Valid @RequestBody SalesSlipCreateRequest request) {
		return ApiResponse.ok(salesSlipCreationService.create(request));
	}
}
