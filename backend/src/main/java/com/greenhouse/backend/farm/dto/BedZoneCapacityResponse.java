package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.BedZoneCapacity;
import com.greenhouse.backend.farm.domain.PlacementCapacityMode;
import java.math.BigDecimal;

public record BedZoneCapacityResponse(
		Long id,
		String placementType,
		String potSize,
		PlacementCapacityMode capacityMode,
		BigDecimal unitSpan,
		Integer capacityValue,
		Boolean allowed,
		String memo) {
	public static BedZoneCapacityResponse from(BedZoneCapacity capacity) {
		return new BedZoneCapacityResponse(
				capacity.getId(),
				capacity.getPlacementType(),
				capacity.getPotSize(),
				capacity.getCapacityMode(),
				capacity.getUnitSpan(),
				capacity.getCapacityValue(),
				capacity.getAllowed(),
				capacity.getMemo());
	}
}
