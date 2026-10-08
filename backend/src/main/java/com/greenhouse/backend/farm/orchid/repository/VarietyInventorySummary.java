package com.greenhouse.backend.farm.orchid.repository;

public record VarietyInventorySummary(
    Long varietyId, long groupCount, long quantity, long saleableQuantity) {}
