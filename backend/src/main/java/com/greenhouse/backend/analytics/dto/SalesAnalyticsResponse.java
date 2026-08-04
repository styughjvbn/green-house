package com.greenhouse.backend.analytics.dto;

import java.util.List;

public record SalesAnalyticsResponse(
		Long currentMonthSales,
		Long previousMonthSales,
		Long shippedQuantity,
		Long previousMonthShippedQuantity,
		Long unpaidAmount,
		Long saleableQuantity,
		List<AnalyticsRankedValueResponse> monthlySales,
		List<AnalyticsRankedValueResponse> varietySales,
		List<VarietyInventoryAnalyticsResponse> varietyInventory,
		List<AnalyticsRankedValueResponse> partnerSales,
		List<AnalyticsRankedValueResponse> paymentBreakdown,
		List<AnalyticsSlipSummaryResponse> recentSlips,
		List<AnalyticsSlipSummaryResponse> unpaidSlips,
		List<AnalyticsInsightResponse> salesInsights) {
}
