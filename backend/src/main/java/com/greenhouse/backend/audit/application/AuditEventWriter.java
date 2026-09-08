package com.greenhouse.backend.audit.application;

import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditEventWriter {
	private final AuditRecorder auditRecorder;
	private final AuditRequestContext requestContext;

	public Long record(AuditAction action, AuditSource source, String entityType, Long entityId,
			Map<String, Object> before, Map<String, Object> after, Map<String, Object> context) {
		return record(action, source, new AuditEvent.Target(entityType, entityId), before, after, context);
	}

	public Long record(AuditAction action, AuditSource source, AuditEvent.Target target,
			Map<String, Object> before, Map<String, Object> after, Map<String, Object> context) {
		return recordChanges(action, source, target, detectChanges(before, after), before, after, context);
	}

	public Long recordChanges(AuditAction action, AuditSource source, AuditEvent.Target target,
			List<String> changedFields, Object before, Object after, Map<String, Object> context) {
		if (changedFields == null || changedFields.isEmpty()) return null;
		return auditRecorder.record(new AuditEvent(requestContext.current(), action, source, target,
				changedFields, before, after, context));
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
