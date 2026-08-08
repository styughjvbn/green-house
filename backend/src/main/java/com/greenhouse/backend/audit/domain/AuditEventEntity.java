package com.greenhouse.backend.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "audit_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEventEntity {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false) private Instant occurredAt;
	private String actorId;
	private String sessionId;
	private String clientInstanceId;
	private String requestId;
	@Enumerated(EnumType.STRING) @Column(nullable = false) private AuditAction action;
	@Enumerated(EnumType.STRING) @Column(nullable = false) private AuditSource source;
	@Column(nullable = false) private String entityType;
	@Column(nullable = false) private Long entityId;
	private Long houseId;
	private Long physicalBedId;
	private Long zoneId;
	private Long varietyId;
	@JdbcTypeCode(SqlTypes.ARRAY) @Column(nullable = false) private String[] changedFields;
	@JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private Object beforeData;
	@JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private Object afterData;
	@JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private Object contextData;

	public AuditEventEntity(Instant occurredAt, String actorId, String sessionId, String clientInstanceId,
			String requestId, AuditAction action, AuditSource source, String entityType, Long entityId,
			Long houseId, Long physicalBedId, Long zoneId, Long varietyId, String[] changedFields,
			Object beforeData, Object afterData, Object contextData) {
		this.occurredAt = occurredAt; this.actorId = actorId; this.sessionId = sessionId;
		this.clientInstanceId = clientInstanceId; this.requestId = requestId; this.action = action;
		this.source = source; this.entityType = entityType; this.entityId = entityId; this.houseId = houseId;
		this.physicalBedId = physicalBedId; this.zoneId = zoneId; this.varietyId = varietyId;
		this.changedFields = changedFields; this.beforeData = beforeData; this.afterData = afterData;
		this.contextData = contextData;
	}
}
