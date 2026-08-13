package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import org.springframework.stereotype.Component;

@Component
public class AuctionShipmentLotFactory {

	public AuctionShipmentLot create(SalesSlipItem item) {
		return new AuctionShipmentLot(
				SalesTextNormalizer.required(
						item.getGenus() == null || item.getGenus().isBlank()
								? item.getItemName()
								: item.getGenus()),
				SalesTextNormalizer.required(item.getItemName()),
				SalesTextNormalizer.normalize(item.getSpec()),
				null,
				item.getQuantity());
	}
}
