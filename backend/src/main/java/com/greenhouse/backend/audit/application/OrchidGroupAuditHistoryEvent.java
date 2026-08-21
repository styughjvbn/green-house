package com.greenhouse.backend.audit.application;

import com.greenhouse.backend.audit.domain.AuditAction;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OrchidGroupAuditHistoryEvent(
		Long auditEventId,
		Instant occurredAt,
		AuditAction action,
		Long orchidGroupId,
		List<String> changedFields,
		Map<String, Object> beforeData,
		Map<String, Object> afterData,
		Map<String, Object> contextData) {

	public OrchidGroupAuditHistoryEvent {
		changedFields = List.copyOf(changedFields);
		beforeData = immutableMap(beforeData);
		afterData = immutableMap(afterData);
		contextData = immutableMap(contextData);
	}

	private static Map<String, Object> immutableMap(Map<String, Object> value) {
		return value == null
				? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(value));
	}
}
