package com.greenhouse.backend.farm.dto.orchid;

import com.greenhouse.backend.farm.dto.structure.PhysicalBedResponse;
import java.util.List;

public record OrchidManagementViewportResponse(
		Long startBedId,
		int bedCount,
		List<PhysicalBedResponse> beds,
		boolean hasPrevious,
		boolean hasNext,
		OrchidManagementSummaryResponse summary,
		List<OrchidManagementBedOrderResponse> bedOrder) {
}
