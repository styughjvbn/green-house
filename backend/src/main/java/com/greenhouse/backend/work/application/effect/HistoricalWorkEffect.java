package com.greenhouse.backend.work.application.effect;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record HistoricalWorkEffect(
		Long effectId,
		Long workOperationId,
		String effectKey,
		String handlerCode,
		Instant appliedAt,
		Map<String, Object> commandDetails,
		Map<String, Object> resultDetails,
		Long mutationId,
		UUID correlationId,
		List<HistoricalWorkEffectLink> links) {

	public HistoricalWorkEffect {
		commandDetails = immutableMap(commandDetails);
		resultDetails = immutableMap(resultDetails);
		links = List.copyOf(links);
	}

	private static Map<String, Object> immutableMap(Map<String, Object> value) {
		return value == null
				? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(value));
	}
}
