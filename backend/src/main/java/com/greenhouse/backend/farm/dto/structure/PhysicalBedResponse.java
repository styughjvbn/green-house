package com.greenhouse.backend.farm.dto.structure;

import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import java.math.BigDecimal;
import java.util.List;

public record PhysicalBedResponse(
		Long id,
		Long houseId,
		Integer houseNumber,
		Integer number,
		Integer displayOrder,
		Integer lengthCm,
		Integer widthCm,
		Integer wireCount,
		Integer supportIntervalCm,
		BigDecimal positionUnitCount,
		String positionUnitLabel,
		String memo,
		List<BedZoneResponse> bedZones) {

	public static PhysicalBedResponse from(PhysicalBed physicalBed) {
		var house = physicalBed.getHouse();
		return new PhysicalBedResponse(
				physicalBed.getId(),
				house.getId(),
				house.getNumber(),
				physicalBed.getNumber(),
				physicalBed.getDisplayOrder(),
				physicalBed.getLengthCm(),
				physicalBed.getWidthCm(),
				physicalBed.getWireCount(),
				physicalBed.getSupportIntervalCm(),
				physicalBed.getPositionUnitCount(),
				physicalBed.getPositionUnitLabel(),
				physicalBed.getMemo(),
				physicalBed.getBedZones().stream().map(BedZoneResponse::from).toList());
	}
}
