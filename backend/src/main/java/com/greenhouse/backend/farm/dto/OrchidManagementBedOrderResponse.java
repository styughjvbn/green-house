package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.PhysicalBed;

public record OrchidManagementBedOrderResponse(
		Long id,
		Long houseId,
		Integer houseNumber,
		Integer number) {

	public static OrchidManagementBedOrderResponse from(PhysicalBed bed) {
		return new OrchidManagementBedOrderResponse(
				bed.getId(),
				bed.getHouse().getId(),
				bed.getHouse().getNumber(),
				bed.getNumber());
	}
}
