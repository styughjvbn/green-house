package com.greenhouse.backend.analytics.dto;

import java.util.List;

public record SalesAnalyticsResponse(
		Long currentMonthSales,
		Long shippedQuantity,
		Long unpaidAmount,
		List<AnalyticsRankedValueResponse> monthlySales,
		List<AnalyticsRankedValueResponse> varietySales,
		List<AnalyticsRankedValueResponse> partnerSales,
		List<AnalyticsRankedValueResponse> paymentBreakdown,
		List<AnalyticsSlipSummaryResponse> recentSlips,
		List<AnalyticsSlipSummaryResponse> unpaidSlips,
		List<AnalyticsInsightResponse> salesInsights) {
}
