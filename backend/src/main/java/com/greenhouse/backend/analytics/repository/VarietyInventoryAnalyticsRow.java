package com.greenhouse.backend.analytics.repository;

public record VarietyInventoryAnalyticsRow(
		String varietyName,
		Long saleableQuantity,
		Long warningGroupCount) {
}
