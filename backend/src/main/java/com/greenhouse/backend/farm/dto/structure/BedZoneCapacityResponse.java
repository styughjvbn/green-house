package com.greenhouse.backend.farm.dto.structure;

import com.greenhouse.backend.farm.domain.structure.BedZoneCapacity;
import com.greenhouse.backend.farm.domain.structure.PlacementCapacityMode;
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
