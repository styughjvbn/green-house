package com.greenhouse.backend.dashboard.dto;

public record DashboardSummaryResponse(
	long houseCount,
	long physicalBedCount,
	long bedZoneCount,
	long orchidGroupCount,
	long warningCount,
	long repotDueCount,
	String latestWorkDate
) { }
