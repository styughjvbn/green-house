package com.greenhouse.backend.sales.payment.web.dto;

import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import com.greenhouse.backend.sales.payment.domain.PartnerPaymentEvent;
import com.greenhouse.backend.sales.payment.domain.PaymentEventStatus;
import com.greenhouse.backend.sales.payment.domain.PaymentEventType;
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
    String createdBy,
    boolean unassignedCancellationAllowed) {
  public static PartnerPaymentEventResponse from(PartnerPaymentEvent event, String partnerName) {
    return from(event, partnerName, false);
  }

  public static PartnerPaymentEventResponse from(
      PartnerPaymentEvent event, String partnerName, boolean reviewRequired) {
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
        event.getCreatedBy(),
        event.isUnassignedCancellationAllowed(reviewRequired));
  }
}
