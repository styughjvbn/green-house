package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.auction.application.AuctionShipmentLifecycleService;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.settlement.application.AuctionSettlementReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionSalesSlipCancellationPolicy {

	private final AuctionShipmentLifecycleService auctionShipmentLifecycleService;
	private final AuctionSettlementReader auctionSettlementReader;

	public void cancelShipmentIfPossible(SalesSlip salesSlip) {
		if (salesSlip.getAuctionShipment() == null) {
			return;
		}

		Long shipmentId = salesSlip.getAuctionShipment().getId();
		if (auctionSettlementReader.existsByAuctionShipmentId(shipmentId)) {
			throw new IllegalArgumentException("정산에 반영된 경매 출하 전표는 취소할 수 없습니다.");
		}

		salesSlip.getItems().forEach(item -> item.clearAuctionShipmentLot());
		var shipment = salesSlip.getAuctionShipment();
		salesSlip.clearAuctionShipment();
		auctionShipmentLifecycleService.deleteDraftShipment(shipment);
	}
}
