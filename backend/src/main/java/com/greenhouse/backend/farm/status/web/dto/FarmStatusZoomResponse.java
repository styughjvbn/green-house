package com.greenhouse.backend.farm.status.web.dto;

import com.greenhouse.backend.farm.status.domain.FarmZoomLevel;
import com.greenhouse.backend.farm.structure.web.dto.BedZoneResponse;
import com.greenhouse.backend.farm.structure.web.dto.PhysicalBedResponse;
import java.util.List;

public record FarmStatusZoomResponse(
    FarmZoomLevel level,
    Long houseId,
    Integer houseNumber,
    List<PhysicalBedResponse> physicalBeds,
    List<BedZoneResponse> bedZones) {}
