package com.greenhouse.backend.farm.api.orchid;

import java.math.BigDecimal;

/** Current values captured together; no managed entities cross the Farm boundary. */
public record OrchidGroupState(
    Long id,
    Long varietyId,
    String varietyName,
    String genus,
    Integer ageYear,
    String potSizeCode,
    String potSize,
    Integer quantity,
    Integer reservedQuantity,
    Integer availableQuantity,
    String status,
    Long houseId,
    Integer houseNumber,
    Long physicalBedId,
    Integer physicalBedNumber,
    Long bedZoneId,
    String bedZoneName,
    BigDecimal startPosition,
    BigDecimal endPosition) {}
