package com.greenhouse.backend.sales.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.sales.application.SalesService;
import com.greenhouse.backend.sales.dto.CustomerCreateRequest;
import com.greenhouse.backend.sales.dto.CustomerResponse;
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

	private final SalesService salesService;

	public SalesController(SalesService salesService) {
		this.salesService = salesService;
	}

	@GetMapping("/customers")
	public ApiResponse<List<CustomerResponse>> getCustomers(@RequestParam(required = false) String keyword) {
		return ApiResponse.ok(salesService.getCustomers(keyword));
	}

	@PostMapping("/customers")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CustomerResponse> createCustomer(@Valid @RequestBody CustomerCreateRequest request) {
		return ApiResponse.ok(salesService.createCustomer(request));
	}

	@GetMapping("/sales-slips")
	public ApiResponse<List<SalesSlipResponse>> getSalesSlips(
		@RequestParam(required = false) Long customerId,
		@RequestParam(required = false) LocalDate from,
		@RequestParam(required = false) LocalDate to
	) {
		return ApiResponse.ok(salesService.getSalesSlips(customerId, from, to));
	}

	@GetMapping("/sales-slips/{salesSlipId}")
	public ApiResponse<SalesSlipResponse> getSalesSlip(@PathVariable Long salesSlipId) {
		return ApiResponse.ok(salesService.getSalesSlip(salesSlipId));
	}

	@GetMapping("/sales-slips/{salesSlipId}/print")
	public ApiResponse<SalesSlipResponse> getSalesSlipPrintData(@PathVariable Long salesSlipId) {
		return ApiResponse.ok(salesService.getSalesSlip(salesSlipId));
	}

	@PostMapping("/sales-slips")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<SalesSlipResponse> createSalesSlip(@Valid @RequestBody SalesSlipCreateRequest request) {
		return ApiResponse.ok(salesService.createSalesSlip(request));
	}
}
