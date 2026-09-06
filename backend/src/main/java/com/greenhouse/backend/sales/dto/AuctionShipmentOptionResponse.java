package com.greenhouse.backend.sales.dto;

import com.greenhouse.backend.auction.application.AuctionDataReader.Shipment;
import java.time.LocalDate;
import java.util.List;

public record AuctionShipmentOptionResponse(
		Long id,
		LocalDate shipmentDate,
		Long auctionHouseId,
		String auctionMarket,
		List<Lot> lots) {
	public static AuctionShipmentOptionResponse from(Shipment shipment) {
		return new AuctionShipmentOptionResponse(
				shipment.id(),
				shipment.shipmentDate(),
				shipment.auctionHouseId(),
				shipment.auctionMarket(),
				shipment.lots().stream()
						.map(lot -> new Lot(
								lot.id(),
								lot.itemName(),
								lot.varietyName(),
								lot.shipmentGrade(),
								lot.shippedQuantity()))
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
