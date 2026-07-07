package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.BedZoneSegmentCapacity;
import com.greenhouse.backend.farm.domain.PlacementCapacityMode;

public record BedZoneCapacityResponse(
		Long id,
		String placementType,
		String potSize,
		PlacementCapacityMode capacityMode,
		Integer capacityValue,
		Boolean allowed,
		String memo) {
	public static BedZoneCapacityResponse from(BedZoneSegmentCapacity capacity) {
		return new BedZoneCapacityResponse(
				capacity.getId(), capacity.getPlacementType(), capacity.getPotSize(), capacity.getCapacityMode(),
				capacity.getCapacityValue(), capacity.getAllowed(), capacity.getMemo());
	}
}
