package com.greenhouse.backend.farm.repository.orchid;

public record VarietyInventorySummary(
    Long varietyId, long groupCount, long quantity, long saleableQuantity) {}
