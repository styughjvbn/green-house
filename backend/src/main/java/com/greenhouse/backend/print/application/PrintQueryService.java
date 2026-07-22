package com.greenhouse.backend.print.application;

import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.sales.application.SalesQueryService;
import com.greenhouse.backend.sales.dto.SalesSlipListItemResponse;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PrintQueryService {
	private final SalesQueryService salesQueryService;

	public PageResponse<SalesSlipListItemResponse> getPrintableSalesSlips(int page, int size) {
		return salesQueryService.getSalesSlipPage(null, null, null, null, null, null, page, size);
	}

	public SalesSlipResponse getSalesSlipPrintData(Long salesSlipId) {
		return salesQueryService.getSalesSlip(salesSlipId);
	}
}
