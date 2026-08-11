package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.sales.domain.SalesSlip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SalesSlipOutboundService {

	private final SalesSlipInventoryService inventoryService;
	private final AuctionShipmentMaterializer auctionShipmentMaterializer;

	public void complete(SalesSlip salesSlip) {
		SalesSlipAllocationBatch allocations = inventoryService.lockForUpdate(salesSlip);
		auctionShipmentMaterializer.materialize(salesSlip);
		inventoryService.outbound(allocations);
	}
}
