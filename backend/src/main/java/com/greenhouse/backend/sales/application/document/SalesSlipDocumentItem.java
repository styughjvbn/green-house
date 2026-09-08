package com.greenhouse.backend.sales.application.document;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupState;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(name = "SalesSlipItemResponse")
public record SalesSlipDocumentItem(
		Long id,
		Long auctionShipmentLotId,
		String itemName,
		String genus,
		String spec,
		Integer quantity,
		Integer unitPrice,
		Integer amount,
		String memo,
		List<SalesSlipDocumentAllocation> allocations) {

	public static SalesSlipDocumentItem from(SalesSlipItem item, List<SalesSlipItemAllocation> allocations,
			Map<Long, OrchidGroupState> states) {
		return new SalesSlipDocumentItem(
				item.getId(),
				item.getAuctionShipmentLotId(),
				item.getItemName(),
				item.getGenus(),
				item.getSpec(),
				item.getQuantity(),
				item.getUnitPrice(),
				item.getAmount(),
				item.getMemo(),
				allocations.stream().map(allocation -> SalesSlipDocumentAllocation.from(
						allocation, states.get(allocation.getOrchidGroupId()))).toList());
	}
}
