package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.auction.application.AuctionShipmentCreator;
import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuctionShipmentMaterializer {

	private final BusinessPartnerReader partnerReader;
	private final AuctionShipmentCreator auctionShipmentCreator;
	private final AuctionShipmentLotFactory auctionShipmentLotFactory;

	public void materialize(SalesSlip salesSlip) {
		if (salesSlip.getSalesType() != SalesType.AUCTION || salesSlip.getAuctionShipmentId() != null) {
			return;
		}
		var partner = partnerReader.getInfo(salesSlip.getPartnerId());
		if (partner.partnerType() != PartnerType.AUCTION_HOUSE) {
			throw new IllegalArgumentException("경매 판매는 경매장 거래처만 선택할 수 있습니다.");
		}

		var shipment = new AuctionShipment(salesSlip.getSaleDate(), partner.id(), partner.partnerType());
		salesSlip.getItems().forEach(item -> shipment.addLot(auctionShipmentLotFactory.create(item)));

		var savedShipment = auctionShipmentCreator.save(shipment);
		salesSlip.assignAuctionShipment(savedShipment.getId());
		for (int index = 0; index < salesSlip.getItems().size(); index++) {
			salesSlip.getItems().get(index).assignAuctionShipmentLot(savedShipment.getLots().get(index).getId());
		}
	}
}
