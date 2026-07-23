package com.greenhouse.backend.farm.dto.status;

import java.util.List;

public record HouseStatusSummaryResponse(
		Long houseId,
		Integer houseNumber,
		String houseName,
		long orchidGroupCount,
		long warningCount,
		long repotDueCount,
		String latestWorkDate,
		List<FarmStatusMapPhysicalBedResponse> physicalBeds) {
}
