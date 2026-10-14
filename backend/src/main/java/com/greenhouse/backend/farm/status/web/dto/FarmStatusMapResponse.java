package com.greenhouse.backend.farm.status.web.dto;

import java.util.List;

public record FarmStatusMapResponse(
    List<HouseStatusSummaryResponse> houses, List<FarmStatusMapOrchidGroupResponse> orchidGroups) {}
