package com.greenhouse.backend.work.application.effect;

import com.greenhouse.backend.farm.domain.OrchidGroup;
import java.util.List;
import java.util.Map;

public record WorkExecutionResult(
		String handlerCode,
		Map<String, Object> resultDetails,
		List<OrchidGroup> resultOrchidGroups) {
}
