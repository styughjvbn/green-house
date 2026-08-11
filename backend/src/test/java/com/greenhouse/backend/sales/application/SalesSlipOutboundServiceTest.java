package com.greenhouse.backend.sales.application;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.sales.domain.SalesSlip;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class SalesSlipOutboundServiceTest {

	@Test
	void locksAllocationsBeforeMaterializingShipmentAndChangingInventory() {
		SalesSlipInventoryService inventoryService = mock(SalesSlipInventoryService.class);
		AuctionShipmentMaterializer materializer = mock(AuctionShipmentMaterializer.class);
		SalesSlip salesSlip = mock(SalesSlip.class);
		SalesSlipAllocationBatch allocations = new SalesSlipAllocationBatch(salesSlip, List.of());
		when(inventoryService.lockForUpdate(salesSlip)).thenReturn(allocations);

		new SalesSlipOutboundService(inventoryService, materializer).complete(salesSlip);

		InOrder order = inOrder(inventoryService, materializer);
		order.verify(inventoryService).lockForUpdate(salesSlip);
		order.verify(materializer).materialize(salesSlip);
		order.verify(inventoryService).outbound(allocations);
	}
}
