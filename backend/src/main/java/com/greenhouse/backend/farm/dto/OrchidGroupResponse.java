package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.OrchidGroup;

public record OrchidGroupResponse(
	Long id,
	Long bedZoneId,
	String genus,
	String varietyName,
	Integer quantity,
	String potSize,
	Integer ageYear,
	String status,
	String placementType,
	Integer trayCount,
	Integer sortOrder,
	String memo,
	Integer houseNumber,
	Integer physicalBedNumber,
	String bedZoneName
) {

	public static OrchidGroupResponse from(OrchidGroup orchidGroup) {
		var bedZone = orchidGroup.getBedZone();
		var physicalBed = bedZone.getPhysicalBed();
		var house = physicalBed.getHouse();
		return new OrchidGroupResponse(
			orchidGroup.getId(),
			bedZone.getId(),
			orchidGroup.getGenus(),
			orchidGroup.getVarietyName(),
			orchidGroup.getQuantity(),
			orchidGroup.getPotSize(),
			orchidGroup.getAgeYear(),
			orchidGroup.getStatus(),
			orchidGroup.getPlacementType(),
			orchidGroup.getTrayCount(),
			orchidGroup.getSortOrder(),
			orchidGroup.getMemo(),
			house.getNumber(),
			physicalBed.getNumber(),
			bedZone.getName()
		);
	}
}
