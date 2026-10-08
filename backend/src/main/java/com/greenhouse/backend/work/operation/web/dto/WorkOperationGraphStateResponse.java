package com.greenhouse.backend.work.operation.web.dto;

import java.math.BigDecimal;

public record WorkOperationGraphStateResponse(
    Integer quantity,
    Integer reservedQuantity,
    String status,
    Long varietyId,
    String genus,
    String varietyName,
    Integer ageYear,
    String potSizeCode,
    Long bedZoneId,
    Integer houseNumber,
    Integer physicalBedNumber,
    String bedZoneName,
    BigDecimal startPosition,
    BigDecimal endPosition) {}
