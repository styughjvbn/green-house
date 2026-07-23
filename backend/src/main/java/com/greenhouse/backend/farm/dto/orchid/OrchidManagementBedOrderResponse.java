package com.greenhouse.backend.farm.dto.orchid;

import com.greenhouse.backend.farm.domain.structure.PhysicalBed;

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
