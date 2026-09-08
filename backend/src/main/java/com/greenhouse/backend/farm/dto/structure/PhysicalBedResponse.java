package com.greenhouse.backend.farm.dto.structure;

import java.time.LocalDate;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

	public static PhysicalBedResponse from(
			PhysicalBed physicalBed,
			Map<Long, List<OrchidGroup>> groupsByZoneId, LocalDate businessDate) {
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
				physicalBed.getBedZones().stream()
						.map(zone -> BedZoneResponse.from(zone, groupsByZoneId.getOrDefault(zone.getId(), List.of()), businessDate))
						.toList());
	}
}
