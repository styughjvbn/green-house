package com.greenhouse.backend.farm.dto;

import java.util.List;

public record FarmStatusMapResponse(
		List<HouseStatusSummaryResponse> houses,
		List<FarmStatusMapOrchidGroupResponse> orchidGroups) {
}
