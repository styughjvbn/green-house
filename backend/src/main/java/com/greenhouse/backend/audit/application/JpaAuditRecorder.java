package com.greenhouse.backend.audit.application;

import com.greenhouse.backend.audit.domain.AuditEventEntity;
import com.greenhouse.backend.audit.repository.AuditEventRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class JpaAuditRecorder implements AuditRecorder {
	private final AuditEventRepository repository;
	private final Clock clock;

	@Override
	public Long record(AuditEvent event) {
		try {
			var saved = repository.save(new AuditEventEntity(Instant.now(clock), event.actorId(), event.sessionId(),
					event.clientInstanceId(), event.requestId(), event.action(), event.source(), event.entityType(),
					event.entityId(), event.houseId(), event.physicalBedId(), event.zoneId(), event.varietyId(),
					event.changedFields().toArray(String[]::new), event.beforeData(), event.afterData(), event.contextData()));
			log.info("event=AUDIT_EVENT_RECORDED auditEventId={} source={} action={} entityType={} entityId={} actorId={} requestId={} changedFields={}",
					saved.getId(), event.source(), event.action(), event.entityType(), event.entityId(), event.actorId(),
					event.requestId(), event.changedFields());
			return saved.getId();
		} catch (RuntimeException exception) {
			log.error("event=AUDIT_EVENT_RECORD_FAILED entityType={} entityId={} requestId={}",
					event.entityType(), event.entityId(), event.requestId(), exception);
			throw exception;
		}
	}
}
