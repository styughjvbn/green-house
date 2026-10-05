package com.greenhouse.backend.farm.repository.orchid;

import java.math.BigDecimal;

public record OrchidPlacementRow(
    Long orchidGroupId,
    Long bedZoneId,
    BigDecimal startPosition,
    BigDecimal endPosition,
    Integer sortOrder) {}
