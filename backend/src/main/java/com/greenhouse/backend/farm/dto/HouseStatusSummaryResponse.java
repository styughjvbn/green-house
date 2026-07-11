package com.greenhouse.backend.farm.dto;

import java.util.List;

public record HouseStatusSummaryResponse(
		Long houseId,
		Integer houseNumber,
		String houseName,
		long orchidGroupCount,
		long warningCount,
		long repotDueCount,
		String latestWorkDate,
		List<PhysicalBedResponse> physicalBeds) {
}
