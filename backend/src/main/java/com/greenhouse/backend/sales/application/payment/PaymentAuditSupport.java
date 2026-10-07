package com.greenhouse.backend.sales.application.payment;

import com.greenhouse.backend.audit.application.AuditEventWriter;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.sales.domain.payment.PartnerPaymentEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PaymentAuditSupport {

  private final AuditEventWriter auditWriter;

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
