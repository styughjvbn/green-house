package com.greenhouse.backend.sales.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.sales.application.SalesOrchidGroupQueryService;
import com.greenhouse.backend.sales.application.SalesPaymentService;
import com.greenhouse.backend.sales.application.SalesQueryService;
import com.greenhouse.backend.sales.application.SalesSlipCreationService;
import com.greenhouse.backend.sales.application.SalesSlipStatusService;
import com.greenhouse.backend.sales.application.SalesSlipUpdateService;
import com.greenhouse.backend.sales.dto.AuctionShipmentOptionResponse;
import com.greenhouse.backend.sales.dto.SalesOrchidGroupSearchResponse;
import com.greenhouse.backend.sales.dto.SalesSlipCreateRequest;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import com.greenhouse.backend.sales.dto.SalesSlipStatusUpdateRequest;
import com.greenhouse.backend.settlement.dto.ManualPaymentRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SalesController {

	private final SalesQueryService salesQueryService;
	private final SalesSlipCreationService salesSlipCreationService;
	private final SalesSlipUpdateService salesSlipUpdateService;
	private final SalesPaymentService salesPaymentService;
	private final SalesSlipStatusService salesSlipStatusService;
	private final SalesOrchidGroupQueryService salesOrchidGroupQueryService;

	@GetMapping("/sales-slips")
	public ApiResponse<List<SalesSlipResponse>> getSalesSlips(
			@RequestParam(required = false) Long partnerId,
			@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to) {
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

	@GetMapping("/sales/orchid-groups/search")
	public ApiResponse<List<SalesOrchidGroupSearchResponse>> searchSalesOrchidGroups(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) Long varietyId,
			@RequestParam(required = false) String status) {
		return ApiResponse.ok(salesOrchidGroupQueryService.search(keyword, varietyId, status));
	}

	@PostMapping("/sales-slips")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<SalesSlipResponse> createSalesSlip(@Valid @RequestBody SalesSlipCreateRequest request) {
		return ApiResponse.ok(salesSlipCreationService.create(request));
	}

	@PutMapping("/sales-slips/{salesSlipId}")
	public ApiResponse<SalesSlipResponse> updateSalesSlip(
			@PathVariable Long salesSlipId,
			@Valid @RequestBody SalesSlipCreateRequest request) {
		return ApiResponse.ok(salesSlipUpdateService.update(salesSlipId, request));
	}

	@PostMapping("/sales-slips/{salesSlipId}/confirm-payment")
	public ApiResponse<SalesSlipResponse> confirmPayment(
			@PathVariable Long salesSlipId,
			@Valid @RequestBody ManualPaymentRequest request) {
		return ApiResponse.ok(salesPaymentService.confirmPayment(salesSlipId, request));
	}

	@PatchMapping("/sales-slips/{salesSlipId}/sales-status")
	public ApiResponse<SalesSlipResponse> updateSalesStatus(
			@PathVariable Long salesSlipId,
			@Valid @RequestBody SalesSlipStatusUpdateRequest request) {
		return ApiResponse.ok(salesSlipStatusService.updateStatus(salesSlipId, request));
	}
}
