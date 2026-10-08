package com.greenhouse.backend.sales.partner.application;

import com.greenhouse.backend.audit.application.AuditEventWriter;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.sales.partner.domain.PartnerSettlementSettings;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PartnerSettingsAuditSupport {

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
}
