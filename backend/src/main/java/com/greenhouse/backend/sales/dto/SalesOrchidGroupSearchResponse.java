package com.greenhouse.backend.sales.dto;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupState;

public record SalesOrchidGroupSearchResponse(Long id, Long varietyId, String varietyName, String genus, String status,
		Integer quantity, Integer reservedQuantity, Integer availableQuantity, String potSize, Integer ageYear,
		Integer houseNumber, Integer physicalBedNumber, String bedZoneName) {

	public static SalesOrchidGroupSearchResponse from(OrchidGroupState group) {
		return new SalesOrchidGroupSearchResponse(group.id(), group.varietyId(), group.varietyName(), group.genus(),
				group.status(), group.quantity(), group.reservedQuantity(), group.availableQuantity(), group.potSize(),
				group.ageYear(), group.houseNumber(), group.physicalBedNumber(), group.bedZoneName());
	}
}
