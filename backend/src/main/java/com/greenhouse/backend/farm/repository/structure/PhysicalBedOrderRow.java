package com.greenhouse.backend.farm.repository.structure;

public record PhysicalBedOrderRow(
		Long id,
		Long houseId,
		Integer houseNumber,
		Integer number) {
}
