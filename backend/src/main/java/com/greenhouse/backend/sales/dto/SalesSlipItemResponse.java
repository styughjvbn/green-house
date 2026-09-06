package com.greenhouse.backend.sales.dto;

import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
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
		return from(item, item.getAllocations());
	}

	public static SalesSlipItemResponse from(SalesSlipItem item, List<SalesSlipItemAllocation> allocations) {
		return new SalesSlipItemResponse(
				item.getId(),
				item.getAuctionShipmentLotId(),
				item.getItemName(),
				item.getGenus(),
				item.getSpec(),
				item.getQuantity(),
				item.getUnitPrice(),
				item.getAmount(),
				item.getMemo(),
				allocations.stream().map(SalesSlipItemAllocationResponse::from).toList());
	}
}
