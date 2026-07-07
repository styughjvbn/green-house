package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.BedZoneSegmentCapacity;
import com.greenhouse.backend.farm.domain.PlacementCapacityMode;
import java.math.BigDecimal;

public record BedZoneCapacityResponse(
		Long id,
		String placementType,
		String potSize,
		PlacementCapacityMode capacityMode,
		Integer capacityValue,
		BigDecimal unitSpan,
		Boolean allowed,
		String memo) {
	public static BedZoneCapacityResponse from(BedZoneSegmentCapacity capacity) {
		return new BedZoneCapacityResponse(
				capacity.getId(), capacity.getPlacementType(), capacity.getPotSize(), capacity.getCapacityMode(),
				capacity.getCapacityValue(), capacity.getUnitSpan(), capacity.getAllowed(), capacity.getMemo());
	}
}
