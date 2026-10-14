package com.greenhouse.backend.work.api.correction;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record WorkQuantityCorrectionInput(
    @NotNull Long executionId,
    @Size(max = 100) Map<@NotNull Long, @NotNull @Min(1) Integer> sourceInputQuantities,
    @NotNull @Min(0) Integer lossQuantity,
    @NotNull @Min(0) Integer increaseQuantity) {}
