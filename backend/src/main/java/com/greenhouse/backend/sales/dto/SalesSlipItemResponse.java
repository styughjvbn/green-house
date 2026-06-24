package com.greenhouse.backend.sales.dto;

import com.greenhouse.backend.sales.domain.SalesSlipItem;

public record SalesSlipItemResponse(
	Long id,
	Long orchidGroupId,
	String itemName,
	String genus,
	String spec,
	Integer quantity,
	Integer unitPrice,
	Integer amount,
	String memo
) {

	public static SalesSlipItemResponse from(SalesSlipItem item) {
		return new SalesSlipItemResponse(
			item.getId(),
			item.getOrchidGroup() == null ? null : item.getOrchidGroup().getId(),
			item.getItemName(),
			item.getGenus(),
			item.getSpec(),
			item.getQuantity(),
			item.getUnitPrice(),
			item.getAmount(),
			item.getMemo()
		);
	}
}
