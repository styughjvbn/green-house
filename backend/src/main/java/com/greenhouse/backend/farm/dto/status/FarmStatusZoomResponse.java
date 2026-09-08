package com.greenhouse.backend.farm.dto.status;

import com.greenhouse.backend.farm.domain.status.FarmZoomLevel;
import com.greenhouse.backend.farm.dto.structure.BedZoneResponse;
import com.greenhouse.backend.farm.dto.structure.PhysicalBedResponse;
import java.util.List;

public record FarmStatusZoomResponse(FarmZoomLevel level, Long houseId, Integer houseNumber,
		List<PhysicalBedResponse> physicalBeds, List<BedZoneResponse> bedZones) {
}
