package com.greenhouse.backend.audit.application;

public interface AuditRecorder {
	Long record(AuditEvent event);
}
