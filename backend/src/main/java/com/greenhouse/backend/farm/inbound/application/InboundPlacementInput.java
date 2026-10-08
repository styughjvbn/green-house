package com.greenhouse.backend.farm.inbound.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record InboundPlacementInput(
    @NotNull @Min(1) Integer quantity,
    @NotNull Long bedZoneId,
    @Size(max = 50) String potSize,
    @Min(0) Integer ageYear,
    @Size(max = 100) String placementType,
    @Min(0) Integer trayCount,
    BigDecimal startPosition,
    BigDecimal endPosition) {}
