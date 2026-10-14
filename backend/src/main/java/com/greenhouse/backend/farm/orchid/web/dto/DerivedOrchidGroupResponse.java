package com.greenhouse.backend.farm.orchid.web.dto;

import com.greenhouse.backend.farm.orchid.domain.PotSizeCode;

public record DerivedOrchidGroupResponse(
    String groupKey,
    Long varietyId,
    String varietyName,
    String genus,
    Integer ageYear,
    PotSizeCode potSizeCode,
    String potSize,
    int orchidGroupCount,
    int totalQuantity,
    int locationCount) {}
