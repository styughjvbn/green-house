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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SettlementAuditSupport {

  private final AuditEventWriter auditWriter;

  Map<String, Object> settingsSnapshot(PartnerSettlementSettings settings) {
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

  void recordSettingsUpdate(
      PartnerSettlementSettings settings, Map<String, Object> before, Map<String, Object> after) {
    auditWriter.record(
        AuditAction.UPDATED,
        AuditSource.SETTLEMENT_MANAGEMENT,
        "PARTNER_SETTLEMENT_SETTINGS",
        settings.getId(),
        before,
        after,
        Map.of("partnerId", settings.getPartnerId()));
  }

  Map<String, Object> auctionPaymentSnapshot(AuctionSettlement settlement) {
    var data = new LinkedHashMap<String, Object>();
    data.put("paidAmount", settlement.getPaidAmount());
    data.put("remainingAmount", settlement.getRemainingAmount());
    data.put("paymentStatus", settlement.getStatus().name());
    return data;
  }

  void recordAuctionPayment(
      AuctionSettlement settlement, Map<String, Object> before, Map<String, Object> after) {
    auditWriter.record(
        AuditAction.UPDATED,
        AuditSource.SETTLEMENT_MANAGEMENT,
        "AUCTION_SETTLEMENT",
        settlement.getId(),
        before,
        after,
        Map.of(
            "partnerId",
            settlement.getAuctionHouseId(),
            "targetType",
            PaymentTargetType.AUCTION_SETTLEMENT.name()));
  }

  void recordManualPayment(PartnerPaymentEvent event) {
    var after = new LinkedHashMap<String, Object>();
    after.put("partnerId", event.getPartnerId());
    after.put("eventType", event.getEventType());
    after.put("eventDate", event.getEventDate());
    after.put("amount", event.getAmount());
    after.put("targetType", event.getTargetType());
    after.put("targetId", event.getTargetId());
    after.put("paymentMethod", event.getPaymentMethod());
    after.put("status", event.getStatus());
    after.put("createdBy", event.getCreatedBy());
    auditWriter.record(
        AuditAction.CREATED,
        AuditSource.SETTLEMENT_MANAGEMENT,
        "PAYMENT_EVENT",
        event.getId(),
        Map.of(),
        after,
        Map.of(
            "partnerId",
            event.getPartnerId(),
            "targetType",
            event.getTargetType().name(),
            "targetId",
            event.getTargetId()));
  }
}
