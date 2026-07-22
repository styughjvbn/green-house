package com.greenhouse.backend.farm.dto.orchid;

public record OrchidManagementSummaryResponse(
		long orchidGroupCount,
		long totalQuantity,
		long abnormalCount,
		long bedZoneCount) {
}
