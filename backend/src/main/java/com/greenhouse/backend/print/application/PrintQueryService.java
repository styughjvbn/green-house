package com.greenhouse.backend.print.application;

import com.greenhouse.backend.sales.application.SalesQueryService;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import org.springframework.stereotype.Service;

@Service
public class PrintQueryService {
	private final SalesQueryService salesQueryService;

	public PrintQueryService(SalesQueryService salesQueryService) {
		this.salesQueryService = salesQueryService;
	}

	public SalesSlipResponse getSalesSlipPrintData(Long salesSlipId) {
		return salesQueryService.getSalesSlip(salesSlipId);
	}
}
