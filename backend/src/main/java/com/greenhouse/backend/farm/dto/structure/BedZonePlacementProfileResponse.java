package com.greenhouse.backend.farm.dto.structure;

import com.greenhouse.backend.farm.domain.structure.BedZone;
import java.math.BigDecimal;
import java.util.List;

public record BedZonePlacementProfileResponse(
		Long bedZoneId,
		String bedZoneName,
		Integer houseNumber,
		Integer physicalBedNumber,
		BigDecimal positionUnitCount,
		String positionUnitLabel,
		List<BedZoneCapacityResponse> capacities) {
	public static BedZonePlacementProfileResponse from(BedZone bedZone) {
		return new BedZonePlacementProfileResponse(
				bedZone.getId(),
				bedZone.getName(),
				bedZone.getPhysicalBed().getHouse().getNumber(),
				bedZone.getPhysicalBed().getNumber(),
				bedZone.getPhysicalBed().getPositionUnitCount(),
				bedZone.getPhysicalBed().getPositionUnitLabel(),
				bedZone.getCapacities().stream()
						.sorted((left, right) -> left.getCapacityMode().compareTo(right.getCapacityMode()))
						.map(BedZoneCapacityResponse::from)
						.toList());
	}
}
