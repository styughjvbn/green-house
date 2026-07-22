package com.greenhouse.backend.work.application.target;

import java.util.Map;

public record ResolvedWorkTarget(
		Long orchidGroupId,
		Long varietyId,
		String varietyName,
		Integer quantity,
		Integer ageYear,
		String potSize,
		String potSizeCode,
		Map<String, Object> location) {
}
