package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.OrchidGroup;

public record FarmStatusOrchidGroupItemResponse(
		Long orchidGroupId,
		String varietyName,
		String genus,
		Integer quantity,
		String status,
		Long houseId,
		Integer houseNumber,
		Long physicalBedId,
		Integer physicalBedNumber,
		String physicalBedName,
		Long bedZoneId,
		String bedZoneName) {

	public static FarmStatusOrchidGroupItemResponse from(OrchidGroup orchidGroup) {
		var bedZone = orchidGroup.getBedZone();
		var physicalBed = bedZone.getPhysicalBed();
		var house = physicalBed.getHouse();
		return new FarmStatusOrchidGroupItemResponse(
				orchidGroup.getId(),
				orchidGroup.getVarietyName(),
				orchidGroup.getGenus(),
				orchidGroup.getQuantity(),
				orchidGroup.getStatus(),
				house.getId(),
				house.getNumber(),
				physicalBed.getId(),
				physicalBed.getNumber(),
				physicalBed.getNumber() + "다이",
				bedZone.getId(),
				bedZone.getName());
	}
}
