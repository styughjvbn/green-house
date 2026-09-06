package com.greenhouse.backend.sales.dto;

import com.greenhouse.backend.auction.domain.AuctionShipment;
import java.time.LocalDate;
import java.util.List;

public record AuctionShipmentOptionResponse(
		Long id,
		LocalDate shipmentDate,
		Long auctionHouseId,
		String auctionMarket,
		List<Lot> lots) {
	public static AuctionShipmentOptionResponse from(AuctionShipment shipment, String auctionMarket) {
		return new AuctionShipmentOptionResponse(
				shipment.getId(),
				shipment.getShipmentDate(),
				shipment.getAuctionHouseId(),
				auctionMarket,
				shipment.getLots().stream()
						.map(lot -> new Lot(
								lot.getId(),
								lot.getItemName(),
								lot.getVarietyName(),
								lot.getShipmentGrade(),
								lot.getShippedQuantity()))
						.toList());
	}

	public record Lot(
			Long id,
			String itemName,
			String varietyName,
			String shipmentGrade,
			Integer shippedQuantity) {
	}
}
