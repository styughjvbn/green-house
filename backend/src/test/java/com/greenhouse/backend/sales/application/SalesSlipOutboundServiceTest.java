package com.greenhouse.backend.sales.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.auction.application.AuctionShipmentCreator.CreatedShipment;
import com.greenhouse.backend.auction.application.AuctionShipmentCreator.LotDraft;
import com.greenhouse.backend.auction.application.AuctionShipmentCreator;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupReader;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupState;
import com.greenhouse.backend.sales.domain.SalesOrchidSnapshotType;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.domain.SalesType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class SalesSlipOutboundServiceTest {

	@Test
	void locksAllocationsBeforeMaterializingShipmentAndChangingInventory() {
		SalesSlipInventoryService inventoryService = mock(SalesSlipInventoryService.class);
		AuctionShipmentCreator creator = mock(AuctionShipmentCreator.class);
		SalesSlip salesSlip = mock(SalesSlip.class);
		var reader = mock(OrchidGroupReader.class);
		var state = mock(OrchidGroupState.class);
		SalesSlipItemAllocation allocation = mock(SalesSlipItemAllocation.class);
		when(allocation.getOrchidGroupId()).thenReturn(7L);
		when(state.id()).thenReturn(7L);
		when(reader.lockStates(List.of(7L))).thenReturn(Map.of(7L, state));
		when(salesSlip.getSalesType()).thenReturn(SalesType.AUCTION);
		when(salesSlip.getAuctionShipmentId()).thenReturn(null);
		when(salesSlip.getPartnerId()).thenReturn(4L);
		var date = LocalDate.of(2026, 8, 12);
		when(salesSlip.getSaleDate()).thenReturn(date);
		var first = new SalesSlipItem(null, " 호접란 ", " 팔레놉시스 ", " 특품 ", 12, 0, null);
		var second = new SalesSlipItem(null, " 호접란 ", null, " ", 3, 0, null);
		org.springframework.test.util.ReflectionTestUtils.setField(first, "id", 9L);
		org.springframework.test.util.ReflectionTestUtils.setField(second, "id", 3L);
		first.addAllocation(allocation);
		when(salesSlip.getItems()).thenReturn(List.of(first, second));
		var allocations = SalesSlipAllocationBatch.from(salesSlip);
		var drafts = List.of(new LotDraft(9L, "팔레놉시스", "호접란", "특품", 12), new LotDraft(3L, "호접란", "호접란", null, 3));
		when(creator.create(date, 4L, drafts)).thenReturn(new CreatedShipment(5L, Map.of(3L, 300L, 9L, 900L)));
		Clock clock = Clock.fixed(Instant.parse("2026-08-12T01:02:03Z"), ZoneOffset.UTC);

		new SalesSlipOutboundService(inventoryService, creator, reader, clock).complete(salesSlip);

		InOrder order = inOrder(reader, inventoryService, allocation, creator, salesSlip);
		order.verify(reader).lockStates(List.of(7L));
		order.verify(allocation)
			.captureSnapshot(argThat(snapshot -> snapshot.getSnapshotType() == SalesOrchidSnapshotType.OUTBOUND
					&& snapshot.getCapturedAt().equals(LocalDateTime.of(2026, 8, 12, 1, 2, 3))
					&& snapshot.getOrchidGroupId().equals(7L)));
		order.verify(creator).create(date, 4L, drafts);
		order.verify(salesSlip).assignAuctionShipment(5L);
		order.verify(inventoryService).outbound(allocations);
		assertThat(first.getAuctionShipmentLotId()).isEqualTo(900L);
		assertThat(second.getAuctionShipmentLotId()).isEqualTo(300L);
	}

}
