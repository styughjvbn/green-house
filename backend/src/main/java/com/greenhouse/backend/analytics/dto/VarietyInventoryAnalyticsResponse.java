package com.greenhouse.backend.analytics.dto;

public record VarietyInventoryAnalyticsResponse(
		String varietyName,
		Long saleableQuantity,
		Long warningGroupCount) {
}
