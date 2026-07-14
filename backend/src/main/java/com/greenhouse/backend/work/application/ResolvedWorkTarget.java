package com.greenhouse.backend.work.application;

import java.util.Map;

public record ResolvedWorkTarget(
		Long orchidGroupId,
		Long varietyId,
		String varietyName,
		Integer quantity,
		Integer ageYear,
		String potSize,
		Map<String, Object> location) {
}
