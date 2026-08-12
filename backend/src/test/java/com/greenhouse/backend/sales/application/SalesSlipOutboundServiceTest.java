package com.greenhouse.backend.sales.application;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.domain.SalesOrchidSnapshotType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class SalesSlipOutboundServiceTest {

	@Test
	void locksAllocationsBeforeMaterializingShipmentAndChangingInventory() {
		SalesSlipInventoryService inventoryService = mock(SalesSlipInventoryService.class);
		AuctionShipmentMaterializer materializer = mock(AuctionShipmentMaterializer.class);
		SalesSlip salesSlip = mock(SalesSlip.class);
		SalesSlipItem item = mock(SalesSlipItem.class);
		SalesSlipItemAllocation allocation = mock(SalesSlipItemAllocation.class);
		SalesSlipAllocationBatch allocations = new SalesSlipAllocationBatch(
				salesSlip,
				List.of(new SalesSlipAllocationBatch.Line(item, allocation)));
		when(inventoryService.lockForOutbound(salesSlip)).thenReturn(allocations);
		Clock clock = Clock.fixed(Instant.parse("2026-08-12T01:02:03Z"), ZoneOffset.UTC);

		new SalesSlipOutboundService(inventoryService, materializer, clock).complete(salesSlip);

		InOrder order = inOrder(inventoryService, allocation, materializer);
		order.verify(inventoryService).lockForOutbound(salesSlip);
		order.verify(allocation).captureSnapshot(
				eq(SalesOrchidSnapshotType.OUTBOUND),
				eq(LocalDateTime.of(2026, 8, 12, 1, 2, 3)));
		order.verify(materializer).materialize(salesSlip);
		order.verify(inventoryService).outbound(allocations);
	}
}
