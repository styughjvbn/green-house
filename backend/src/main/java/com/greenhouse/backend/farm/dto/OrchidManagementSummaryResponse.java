package com.greenhouse.backend.farm.dto;

public record OrchidManagementSummaryResponse(
		long orchidGroupCount,
		long totalQuantity,
		long abnormalCount,
		long bedZoneCount) {
}
