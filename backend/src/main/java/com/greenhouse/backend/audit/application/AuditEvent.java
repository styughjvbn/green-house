package com.greenhouse.backend.audit.application;

import java.util.List;
import java.util.Map;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;

public record AuditEvent(
		String actorId,
		String sessionId,
		String clientInstanceId,
		String requestId,
		AuditAction action,
		AuditSource source,
		String entityType,
		Long entityId,
		Long houseId,
		Long physicalBedId,
		Long zoneId,
		Long varietyId,
		List<String> changedFields,
		Object beforeData,
		Object afterData,
		Map<String, Object> contextData
) {
}
