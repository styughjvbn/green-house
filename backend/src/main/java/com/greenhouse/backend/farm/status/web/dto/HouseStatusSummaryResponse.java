package com.greenhouse.backend.farm.status.web.dto;

import java.util.List;

public record HouseStatusSummaryResponse(
    Long houseId,
    Integer houseNumber,
    String houseName,
    long orchidGroupCount,
    long warningCount,
    long repotDueCount,
    String latestWorkDate,
    List<FarmStatusMapPhysicalBedResponse> physicalBeds) {}
