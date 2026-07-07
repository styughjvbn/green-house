package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.BedZone;
import java.math.BigDecimal;
import java.util.List;

public record BedZonePlacementProfileResponse(
		Long bedZoneId,
		String bedZoneName,
		Integer houseNumber,
		Integer physicalBedNumber,
		BigDecimal positionUnitCount,
		String positionUnitLabel,
		Boolean hasUnassignedGroups,
		List<BedZoneSegmentResponse> segments) {
	public static BedZonePlacementProfileResponse from(BedZone bedZone) {
		return new BedZonePlacementProfileResponse(
				bedZone.getId(), bedZone.getName(), bedZone.getPhysicalBed().getHouse().getNumber(),
				bedZone.getPhysicalBed().getNumber(), bedZone.getPhysicalBed().getPositionUnitCount(),
				bedZone.getPhysicalBed().getPositionUnitLabel(),
				bedZone.getOrchidGroups().stream().anyMatch(group -> group.getSegmentPlacements().isEmpty()),
				bedZone.getSegments().stream().map(BedZoneSegmentResponse::from).toList());
	}
}
