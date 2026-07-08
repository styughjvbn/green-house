package com.greenhouse.backend.analytics.dto;

public record AnalyticsRankedValueResponse(String label, Long value, String secondary) {
	public AnalyticsRankedValueResponse(String label, Long value) {
		this(label, value, null);
	}
}
