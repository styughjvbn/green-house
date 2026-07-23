package com.greenhouse.backend.sales.dto;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;

public record SalesOrchidGroupSearchResponse(
		Long id,
		Long varietyId,
		String varietyName,
		String genus,
		String status,
		Integer quantity,
		Integer reservedQuantity,
		Integer availableQuantity,
		String potSize,
		Integer ageYear,
		Integer houseNumber,
		Integer physicalBedNumber,
		String bedZoneName) {

	public static SalesOrchidGroupSearchResponse from(OrchidGroup orchidGroup) {
		var zone = orchidGroup.getBedZone();
		var bed = zone.getPhysicalBed();
		return new SalesOrchidGroupSearchResponse(
				orchidGroup.getId(),
				orchidGroup.getVariety() == null ? null : orchidGroup.getVariety().getId(),
				orchidGroup.getVarietyName(),
				orchidGroup.getGenus(),
				orchidGroup.getStatus(),
				orchidGroup.getQuantity(),
				orchidGroup.getReservedQuantity(),
				orchidGroup.getAvailableQuantity(),
				orchidGroup.getPotSize(),
				orchidGroup.getAgeYear(),
				bed.getHouse().getNumber(),
				bed.getNumber(),
				zone.getName());
	}
}
