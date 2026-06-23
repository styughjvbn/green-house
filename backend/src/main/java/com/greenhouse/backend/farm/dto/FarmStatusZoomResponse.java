package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.FarmZoomLevel;
import java.util.List;

public record FarmStatusZoomResponse(
	FarmZoomLevel level,
	Long houseId,
	Integer houseNumber,
	List<PhysicalBedResponse> physicalBeds,
	List<BedZoneResponse> bedZones
) {
}
