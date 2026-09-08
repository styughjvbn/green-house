package com.greenhouse.backend.audit.application;

import com.greenhouse.backend.audit.domain.AuditEventEntity;
import com.greenhouse.backend.audit.repository.AuditEventRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class JpaAuditRecorder implements AuditRecorder {
	private final AuditEventRepository repository;
	private final Clock clock;

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Long record(AuditEvent event) {
		try {
			var saved = repository.save(new AuditEventEntity(Instant.now(clock), event.identity().actorId(), event.identity().sessionId(),
					event.identity().clientInstanceId(), event.identity().requestId(), event.action(), event.source(), event.target().entityType(),
					event.target().entityId(), event.target().houseId(), event.target().physicalBedId(), event.target().zoneId(), event.target().varietyId(),
					event.changedFields().toArray(String[]::new), event.beforeData(), event.afterData(), event.contextData()));
			log.info("event=AUDIT_EVENT_RECORDED auditEventId={} source={} action={} entityType={} entityId={} actorId={} requestId={} changedFields={}",
					saved.getId(), event.source(), event.action(), event.target().entityType(), event.target().entityId(), event.identity().actorId(),
					event.identity().requestId(), event.changedFields());
			return saved.getId();
		} catch (RuntimeException exception) {
			log.error("event=AUDIT_EVENT_RECORD_FAILED entityType={} entityId={} requestId={}",
					event.target().entityType(), event.target().entityId(), event.identity().requestId(), exception);
			throw exception;
		}
	}
}
