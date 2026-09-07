package com.greenhouse.backend.sales.dto;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupState;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import java.util.List;
import java.util.Map;

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

	public static SalesSlipItemResponse from(SalesSlipItem item, List<SalesSlipItemAllocation> allocations,
			Map<Long, OrchidGroupState> states) {
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
				allocations.stream().map(allocation -> SalesSlipItemAllocationResponse.from(
						allocation, states.get(allocation.getOrchidGroupId()))).toList());
	}
}
