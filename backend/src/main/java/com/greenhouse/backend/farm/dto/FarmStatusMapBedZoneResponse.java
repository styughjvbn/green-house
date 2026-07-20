package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.BedZone;
import com.greenhouse.backend.farm.domain.BedZoneSide;
import com.greenhouse.backend.farm.domain.BedZoneType;

public record FarmStatusMapBedZoneResponse(
		Long id,
		Long physicalBedId,
		Integer physicalBedNumber,
		Long houseId,
		Integer houseNumber,
		String name,
		BedZoneSide side,
		BedZoneType zoneType,
		Integer sortOrder,
		Boolean active,
		String memo) {

	public static FarmStatusMapBedZoneResponse from(BedZone bedZone) {
		var physicalBed = bedZone.getPhysicalBed();
		var house = physicalBed.getHouse();
		return new FarmStatusMapBedZoneResponse(
				bedZone.getId(),
				physicalBed.getId(),
				physicalBed.getNumber(),
				house.getId(),
				house.getNumber(),
				bedZone.getName(),
				bedZone.getSide(),
				bedZone.getZoneType(),
				bedZone.getSortOrder(),
				bedZone.getActive(),
				bedZone.getMemo());
	}
}
