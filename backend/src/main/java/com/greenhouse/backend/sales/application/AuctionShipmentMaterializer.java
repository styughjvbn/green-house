package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.auction.application.AuctionShipmentCreator;
import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionShipmentMaterializer {

	private final AuctionShipmentCreator auctionShipmentCreator;

	public void materialize(SalesSlip salesSlip) {
		if (salesSlip.getSalesType() != SalesType.AUCTION || salesSlip.getAuctionShipment() != null) {
			return;
		}
		if (salesSlip.getPartner().getPartnerType() != PartnerType.AUCTION_HOUSE) {
			throw new IllegalArgumentException("경매 판매는 경매장 거래처만 선택할 수 있습니다.");
		}

		var shipment = new AuctionShipment(salesSlip.getSaleDate(), salesSlip.getPartner());
		salesSlip.getItems().forEach(item -> shipment.addLot(new AuctionShipmentLot(
				SalesTextNormalizer.required(item.getGenus() == null || item.getGenus().isBlank() ? item.getItemName() : item.getGenus()),
				SalesTextNormalizer.required(item.getItemName()),
				SalesTextNormalizer.normalize(item.getSpec()),
				null,
				item.getQuantity())));

		var savedShipment = auctionShipmentCreator.save(shipment);
		salesSlip.assignAuctionShipment(savedShipment);
		for (int index = 0; index < salesSlip.getItems().size(); index++) {
			salesSlip.getItems().get(index).assignAuctionShipmentLot(savedShipment.getLots().get(index));
		}
	}
}
