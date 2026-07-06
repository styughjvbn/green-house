package com.greenhouse.backend.print.controller;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.print.application.PrintQueryService;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales-slips")
public class PrintController {
	private final PrintQueryService printQueryService;

	public PrintController(PrintQueryService printQueryService) {
		this.printQueryService = printQueryService;
	}

	@GetMapping("/{salesSlipId}/print")
	public ApiResponse<SalesSlipResponse> getSalesSlipPrintData(@PathVariable Long salesSlipId) {
		return ApiResponse.ok(printQueryService.getSalesSlipPrintData(salesSlipId));
	}
}
