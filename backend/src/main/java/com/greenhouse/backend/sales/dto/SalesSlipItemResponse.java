package com.greenhouse.backend.sales.dto;

import com.greenhouse.backend.sales.domain.SalesSlipItem;
import java.util.List;

public record SalesSlipItemResponse(
		Long id,
		Long auctionShipmentLotId,
		String itemName,
		String genus,
		String spec,
		Integer quantity,
		Integer unitPrice,
		Integer amount,
		String memo,
		List<SalesSlipItemAllocationResponse> allocations) {

	public static SalesSlipItemResponse from(SalesSlipItem item) {
		return new SalesSlipItemResponse(
				item.getId(),
				item.getAuctionShipmentLot() == null ? null : item.getAuctionShipmentLot().getId(),
				item.getItemName(),
				item.getGenus(),
				item.getSpec(),
				item.getQuantity(),
				item.getUnitPrice(),
				item.getAmount(),
				item.getMemo(),
				item.getAllocations().stream().map(SalesSlipItemAllocationResponse::from).toList());
	}
}
