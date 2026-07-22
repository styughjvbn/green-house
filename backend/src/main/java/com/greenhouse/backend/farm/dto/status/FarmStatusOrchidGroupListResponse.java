package com.greenhouse.backend.farm.dto.status;

import com.greenhouse.backend.farm.domain.status.FarmStatusTargetType;
import java.util.List;

public record FarmStatusOrchidGroupListResponse(
		FarmStatusTargetType targetType,
		Long targetId,
		String targetName,
		List<FarmStatusOrchidGroupItemResponse> items) {
}
