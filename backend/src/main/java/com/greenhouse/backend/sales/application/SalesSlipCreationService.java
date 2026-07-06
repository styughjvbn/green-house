package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.dto.SalesSlipCreateRequest;
import com.greenhouse.backend.sales.dto.SalesSlipResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SalesSlipCreationService {
	private final DirectSalesSlipCreator directSalesSlipCreator;
	private final AuctionSalesSlipCreator auctionSalesSlipCreator;

	public SalesSlipCreationService(
		DirectSalesSlipCreator directSalesSlipCreator,
		AuctionSalesSlipCreator auctionSalesSlipCreator
	) {
		this.directSalesSlipCreator = directSalesSlipCreator;
		this.auctionSalesSlipCreator = auctionSalesSlipCreator;
	}

	public SalesSlipResponse create(SalesSlipCreateRequest request) {
		SalesType salesType = request.salesType() == null ? SalesType.DIRECT : request.salesType();
		return salesType == SalesType.AUCTION
			? auctionSalesSlipCreator.create(request)
			: directSalesSlipCreator.create(request);
	}
}
