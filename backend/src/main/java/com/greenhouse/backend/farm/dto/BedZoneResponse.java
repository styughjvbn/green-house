package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.BedZone;
import com.greenhouse.backend.farm.domain.BedZoneSide;
import com.greenhouse.backend.farm.domain.BedZoneType;
import java.util.List;

public record BedZoneResponse(
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
		String memo,
		List<OrchidGroupResponse> orchidGroups) {

	public static BedZoneResponse from(BedZone bedZone) {
		var physicalBed = bedZone.getPhysicalBed();
		var house = physicalBed.getHouse();
		return new BedZoneResponse(
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
				bedZone.getMemo(),
				bedZone.getOrchidGroups().stream().map(OrchidGroupResponse::from).toList());
	}
}
