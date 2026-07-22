package com.greenhouse.backend.farm.dto.transformation;

import java.util.List;

public record OrchidGroupLineageResponse(
		Long orchidGroupId,
		List<OrchidGroupLineageItemResponse> sources,
		List<OrchidGroupLineageItemResponse> results) {
}
