package com.greenhouse.backend.print.application;

import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.sales.application.SalesQueryService;
import com.greenhouse.backend.sales.application.document.SalesSlipDocument;
import com.greenhouse.backend.sales.application.document.SalesSlipSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class PrintQueryService {

	private final SalesQueryService salesQueryService;

	public PageResponse<SalesSlipSummary> getPrintableSalesSlips(int page, int size) {
		return salesQueryService.getSalesSlipPage(null, null, null, null, null, null, page, size);
	}

	public SalesSlipDocument getSalesSlipPrintData(Long salesSlipId) {
		return salesQueryService.getSalesSlip(salesSlipId);
	}

}
