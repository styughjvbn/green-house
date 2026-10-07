package com.greenhouse.backend.sales.dto.payment;

import com.greenhouse.backend.sales.domain.payment.PartnerPaymentEvent;
import com.greenhouse.backend.sales.domain.payment.PaymentEventStatus;
import com.greenhouse.backend.sales.domain.payment.PaymentEventType;
import com.greenhouse.backend.sales.domain.payment.PaymentTargetType;
import java.time.LocalDate;

public record PartnerPaymentEventResponse(
    Long id,
    Long partnerId,
    String partnerName,
    PaymentEventType eventType,
    LocalDate eventDate,
    Long amount,
    Long unappliedAmount,
    PaymentTargetType targetType,
    Long targetId,
    Long parentEventId,
    String paymentMethod,
    String depositorName,
    String description,
    PaymentEventStatus status,
    String memo,
    String createdBy) {
  public static PartnerPaymentEventResponse from(PartnerPaymentEvent event, String partnerName) {
    return new PartnerPaymentEventResponse(
        event.getId(),
        event.getPartnerId(),
        partnerName,
        event.getEventType(),
        event.getEventDate(),
        event.getAmount(),
        event.getUnappliedAmount(),
        event.getTargetType(),
        event.getTargetId(),
        event.getParentEvent() == null ? null : event.getParentEvent().getId(),
        event.getPaymentMethod(),
        event.getDepositorName(),
        event.getDescription(),
        event.getStatus(),
        event.getMemo(),
        event.getCreatedBy());
  }
}
