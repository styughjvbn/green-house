package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.FarmStatusTargetType;
import java.util.List;

public record FarmStatusOrchidGroupListResponse(
	FarmStatusTargetType targetType,
	Long targetId,
	String targetName,
	List<FarmStatusOrchidGroupItemResponse> items
) {
}
