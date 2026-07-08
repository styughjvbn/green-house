package com.greenhouse.backend.analytics.dto;

import java.util.List;

public record PartnerAnalyticsResponse(
		List<PartnerAnalyticsStatResponse> partnerStats,
		List<AnalyticsRankedValueResponse> partnerSales) {
}
