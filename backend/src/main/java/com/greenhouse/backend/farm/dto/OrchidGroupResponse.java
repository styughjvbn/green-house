package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.OrchidGroup;
import java.math.BigDecimal;

public record OrchidGroupResponse(
		Long id,
		Long bedZoneId,
		Long varietyId,
		String genus,
		String varietyName,
		Integer quantity,
		String potSize,
		Integer ageYear,
		String status,
		String placementType,
		Integer trayCount,
		Boolean splitPlacementAllowed,
		BigDecimal startPosition,
		BigDecimal endPosition,
		Integer sortOrder,
		String memo,
		Integer houseNumber,
		Integer physicalBedNumber,
		String bedZoneName) {

	public static OrchidGroupResponse from(OrchidGroup orchidGroup) {
		var bedZone = orchidGroup.getBedZone();
		var physicalBed = bedZone.getPhysicalBed();
		var house = physicalBed.getHouse();
		var variety = orchidGroup.getVariety();
		return new OrchidGroupResponse(
				orchidGroup.getId(),
				bedZone.getId(),
				variety != null ? variety.getId() : null,
				variety != null ? variety.getGenus() : orchidGroup.getGenus(),
				variety != null ? variety.getName() : orchidGroup.getVarietyName(),
				orchidGroup.getQuantity(),
				orchidGroup.getPotSize(),
				orchidGroup.getAgeYear(),
				orchidGroup.getStatus(),
				orchidGroup.getPlacementType(),
				orchidGroup.getTrayCount(),
				orchidGroup.getSplitPlacementAllowed(),
				orchidGroup.getStartPosition(),
				orchidGroup.getEndPosition(),
				orchidGroup.getSortOrder(),
				orchidGroup.getMemo(),
				house.getNumber(),
				physicalBed.getNumber(),
				bedZone.getName());
	}
}
