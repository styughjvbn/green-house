package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesOrchidSnapshotType;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SalesSlipOutboundService {

	private final SalesSlipInventoryService inventoryService;
	private final AuctionShipmentMaterializer auctionShipmentMaterializer;
	private final Clock clock;

	public void complete(SalesSlip salesSlip) {
		SalesSlipAllocationBatch allocations = inventoryService.lockForOutbound(salesSlip);
		allocations.captureSnapshot(SalesOrchidSnapshotType.OUTBOUND, TimeConfig.utcNow(clock));
		auctionShipmentMaterializer.materialize(salesSlip);
		inventoryService.outbound(allocations);
	}
}
