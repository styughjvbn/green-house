package com.greenhouse.backend.analytics.dto;

public record AnalyticsInsightResponse(
		String tone,
		String text,
		String actionLabel,
		String actionHref) {
}
