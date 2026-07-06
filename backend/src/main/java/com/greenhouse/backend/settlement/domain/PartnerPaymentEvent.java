package com.greenhouse.backend.settlement.domain;

import com.greenhouse.backend.common.domain.BaseEntity;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
	name = "partner_payment_events",
	indexes = {
		@Index(name = "idx_partner_payment_partner_date", columnList = "partner_id,event_date"),
		@Index(name = "idx_partner_payment_target", columnList = "target_type,target_id")
	})
public class PartnerPaymentEvent extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "partner_id", nullable = false)
	private BusinessPartner partner;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false)
	private PaymentEventType eventType;

	@Column(name = "event_date", nullable = false)
	private LocalDate eventDate;

	@Column(name = "event_time")
	private LocalTime eventTime;

	@Column(nullable = false)
	private Long amount;

	@Column(name = "unapplied_amount", nullable = false)
	private Long unappliedAmount;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type")
	private PaymentTargetType targetType;

	@Column(name = "target_id")
	private Long targetId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_event_id")
	private PartnerPaymentEvent parentEvent;

	@Column(name = "payment_method")
	private String paymentMethod;

	@Column(name = "depositor_name")
	private String depositorName;

	@Column(columnDefinition = "text")
	private String description;

	@Column(name = "external_uid", unique = true)
	private String externalUid;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentEventStatus status;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "raw_payload", columnDefinition = "jsonb")
	private Map<String, Object> rawPayload;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "match_payload", columnDefinition = "jsonb")
	private Map<String, Object> matchPayload;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "allocation_payload", columnDefinition = "jsonb")
	private Map<String, Object> allocationPayload;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "balance_snapshot_json", columnDefinition = "jsonb")
	private Map<String, Object> balanceSnapshotJson;

	@Column(columnDefinition = "text")
	private String memo;

	@Column(name = "created_by")
	private String createdBy;

	protected PartnerPaymentEvent() { }

	private PartnerPaymentEvent(
		BusinessPartner partner,
		PaymentEventType eventType,
		LocalDate eventDate,
		Long amount,
		PaymentTargetType targetType,
		Long targetId,
		PartnerPaymentEvent parentEvent,
		String paymentMethod,
		String depositorName,
		String description,
		PaymentEventStatus status,
		String memo,
		String createdBy
	) {
		this.partner = partner;
		this.eventType = eventType;
		this.eventDate = eventDate;
		this.amount = amount;
		this.unappliedAmount = 0L;
		this.targetType = targetType;
		this.targetId = targetId;
		this.parentEvent = parentEvent;
		this.paymentMethod = paymentMethod;
		this.depositorName = depositorName;
		this.description = description;
		this.status = status;
		this.memo = memo;
		this.createdBy = createdBy;
	}

	public static PartnerPaymentEvent received(
		BusinessPartner partner,
		LocalDate eventDate,
		Long amount,
		PaymentTargetType targetType,
		Long targetId,
		String paymentMethod,
		String depositorName,
		String memo,
		String createdBy
	) {
		return new PartnerPaymentEvent(
			partner, PaymentEventType.PAYMENT_RECEIVED, eventDate, amount, targetType, targetId, null,
			paymentMethod, depositorName, "수동 입금 확인", PaymentEventStatus.FULLY_APPLIED, memo, createdBy);
	}

	public static PartnerPaymentEvent manualMatch(PartnerPaymentEvent receivedEvent) {
		return new PartnerPaymentEvent(
			receivedEvent.partner, PaymentEventType.MANUAL_MATCH_CONFIRMED, receivedEvent.eventDate,
			receivedEvent.amount, receivedEvent.targetType, receivedEvent.targetId, receivedEvent,
			receivedEvent.paymentMethod, receivedEvent.depositorName, "수동 입금 연결",
			PaymentEventStatus.CONFIRMED, receivedEvent.memo, receivedEvent.createdBy);
	}

	public Long getId() { return id; }
	public BusinessPartner getPartner() { return partner; }
	public PaymentEventType getEventType() { return eventType; }
	public LocalDate getEventDate() { return eventDate; }
	public Long getAmount() { return amount; }
	public Long getUnappliedAmount() { return unappliedAmount; }
	public PaymentTargetType getTargetType() { return targetType; }
	public Long getTargetId() { return targetId; }
	public PartnerPaymentEvent getParentEvent() { return parentEvent; }
	public String getPaymentMethod() { return paymentMethod; }
	public String getDepositorName() { return depositorName; }
	public String getDescription() { return description; }
	public PaymentEventStatus getStatus() { return status; }
	public String getMemo() { return memo; }
	public String getCreatedBy() { return createdBy; }
}
