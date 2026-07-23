package com.greenhouse.backend.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record WorkAnalyticsResponse(
		Long totalCount,
		Long movementCount,
		Long statusCount,
		LocalDate latestWorkDate,
		List<AnalyticsRankedValueResponse> workTypeCounts,
		List<WorkAnalyticsItemResponse> recentRecords) {
}
