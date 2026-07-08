package com.greenhouse.backend.print.application;

import com.greenhouse.backend.sales.application.SalesQueryService;
import com.greenhouse.backend.sales.dto.SalesSlipPageResponse;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PrintQueryService {
	private final SalesQueryService salesQueryService;

	public SalesSlipPageResponse getPrintableSalesSlips(int page, int size) {
		return salesQueryService.getSalesSlipPage(null, null, null, null, null, null, page, size);
	}

	public SalesSlipResponse getSalesSlipPrintData(Long salesSlipId) {
		return salesQueryService.getSalesSlip(salesSlipId);
	}
}
