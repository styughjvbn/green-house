package com.greenhouse.backend.audit.application;

import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class AuditEventWriter {
	private final AuditRecorder auditRecorder;
	private final AuditRequestContext requestContext;

	public AuditEventWriter(AuditRecorder auditRecorder, AuditRequestContext requestContext) {
		this.auditRecorder = auditRecorder;
		this.requestContext = requestContext;
	}

	public Long record(
			AuditAction action,
			AuditSource source,
			String entityType,
			Long entityId,
			Map<String, Object> beforeData,
			Map<String, Object> afterData,
			Map<String, Object> contextData) {
		return record(action, source, entityType, entityId, null, null, null, null,
				beforeData, afterData, contextData);
	}

	public Long record(
			AuditAction action,
			AuditSource source,
			String entityType,
			Long entityId,
			Long houseId,
			Long physicalBedId,
			Long zoneId,
			Long varietyId,
			Map<String, Object> beforeData,
			Map<String, Object> afterData,
			Map<String, Object> contextData) {
		List<String> changedFields = detectChanges(beforeData, afterData);
		if (changedFields.isEmpty()) return null;
		var identity = requestContext.current();
		return auditRecorder.record(new AuditEvent(identity.actorId(), identity.sessionId(),
				identity.clientInstanceId(), identity.requestId(), action, source, entityType, entityId,
				houseId, physicalBedId, zoneId, varietyId, changedFields, beforeData, afterData,
				contextData == null ? Map.of() : contextData));
	}

	public List<String> detectChanges(Map<String, Object> beforeData, Map<String, Object> afterData) {
		var keys = new LinkedHashSet<String>();
		if (beforeData != null) keys.addAll(beforeData.keySet());
		if (afterData != null) keys.addAll(afterData.keySet());
		var changed = new ArrayList<String>();
		for (String key : keys) {
			Object before = beforeData == null ? null : beforeData.get(key);
			Object after = afterData == null ? null : afterData.get(key);
			if (!Objects.equals(before, after)) changed.add(key);
		}
		return List.copyOf(changed);
	}
}
