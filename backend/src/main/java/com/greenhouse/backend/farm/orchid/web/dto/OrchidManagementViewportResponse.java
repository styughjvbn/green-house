package com.greenhouse.backend.farm.orchid.web.dto;

import com.greenhouse.backend.farm.structure.web.dto.PhysicalBedResponse;
import java.util.List;

public record OrchidManagementViewportResponse(
    Long startBedId,
    int bedCount,
    List<PhysicalBedResponse> beds,
    boolean hasPrevious,
    boolean hasNext,
    OrchidManagementSummaryResponse summary) {}
