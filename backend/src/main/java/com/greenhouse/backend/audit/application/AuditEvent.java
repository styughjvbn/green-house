package com.greenhouse.backend.audit.application;

import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AuditEvent(Identity identity, AuditAction action, AuditSource source, Target target,
		List<String> changedFields, Object beforeData, Object afterData, Map<String, Object> contextData) {

	public AuditEvent {
		changedFields = List.copyOf(changedFields);
		contextData = contextData == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(contextData));
	}

	public record Identity(String actorId, String sessionId, String clientInstanceId, String requestId) {
	}

	public record Target(String entityType, Long entityId, Long houseId, Long physicalBedId, Long zoneId,
			Long varietyId) {
		public Target(String entityType, Long entityId) {
			this(entityType, entityId, null, null, null, null);
		}
	}
}
