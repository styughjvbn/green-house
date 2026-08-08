package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.audit.application.AuditEventWriter;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.settlement.domain.AuctionSettlement;
import com.greenhouse.backend.settlement.domain.PartnerPaymentEvent;
import com.greenhouse.backend.settlement.domain.PartnerSettlementSettings;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SettlementAuditSupport {
	private final AuditEventWriter auditWriter;

	public SettlementAuditSupport(AuditEventWriter auditWriter) {
		this.auditWriter = auditWriter;
	}

	public Map<String, Object> settingsSnapshot(PartnerSettlementSettings settings) {
		var data = new LinkedHashMap<String, Object>();
		data.put("settlementUnit", settings.getSettlementUnit());
		data.put("paymentDelayDays", settings.getPaymentDelayDays());
		data.put("paymentDayMode", settings.getPaymentDayMode());
		data.put("autoMatchEnabled", settings.isAutoMatchEnabled());
		data.put("autoSettleEnabled", settings.isAutoSettleEnabled());
		data.put("amountTolerance", settings.getAmountTolerance());
		data.put("depositorAliasCount", settings.getDepositorAliases().size());
		data.put("allowPrepayment", settings.isAllowPrepayment());
		data.put("creditAutoApplyEnabled", settings.isCreditAutoApplyEnabled());
		data.put("ruleJson", settings.getRuleJson());
		return data;
	}

	public void recordSettingsUpdate(PartnerSettlementSettings settings,
			Map<String, Object> before, Map<String, Object> after) {
		auditWriter.record(AuditAction.UPDATED, AuditSource.SETTLEMENT_MANAGEMENT,
				"PARTNER_SETTLEMENT_SETTINGS", settings.getId(), before, after,
				Map.of("partnerId", settings.getPartner().getId()));
	}

	public Map<String, Object> auctionPaymentSnapshot(AuctionSettlement settlement) {
		return paymentSnapshot(settlement.getPaidAmount(), settlement.getRemainingAmount(),
				settlement.getStatus().name());
	}

	public Map<String, Object> paymentSnapshot(Long paidAmount, Long remainingAmount, String status) {
		var data = new LinkedHashMap<String, Object>();
		data.put("paidAmount", paidAmount);
		data.put("remainingAmount", remainingAmount);
		data.put("paymentStatus", status);
		return data;
	}

	public void recordTargetPayment(String entityType, Long entityId, Long partnerId,
			PaymentTargetType targetType, Map<String, Object> before, Map<String, Object> after) {
		auditWriter.record(AuditAction.UPDATED, AuditSource.SETTLEMENT_MANAGEMENT,
				entityType, entityId, before, after,
				Map.of("partnerId", partnerId, "targetType", targetType.name()));
	}

	public void recordManualPayment(PartnerPaymentEvent event) {
		var after = new LinkedHashMap<String, Object>();
		after.put("partnerId", event.getPartner().getId());
		after.put("eventType", event.getEventType());
		after.put("eventDate", event.getEventDate());
		after.put("amount", event.getAmount());
		after.put("targetType", event.getTargetType());
		after.put("targetId", event.getTargetId());
		after.put("paymentMethod", event.getPaymentMethod());
		after.put("status", event.getStatus());
		after.put("createdBy", event.getCreatedBy());
		auditWriter.record(AuditAction.CREATED, AuditSource.SETTLEMENT_MANAGEMENT,
				"PAYMENT_EVENT", event.getId(), Map.of(), after,
				Map.of("partnerId", event.getPartner().getId(),
						"targetType", event.getTargetType().name(), "targetId", event.getTargetId()));
	}
}
