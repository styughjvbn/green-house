package com.greenhouse.backend.sales.dto.payment;

import com.greenhouse.backend.sales.domain.payment.PartnerPaymentEvent;
import com.greenhouse.backend.sales.domain.payment.PaymentEventStatus;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import java.time.LocalDate;

public record PaymentAllocationResponse(
    Long id,
    Long receiptId,
    PaymentTargetType targetType,
    Long targetId,
    Long amount,
    LocalDate allocationDate,
    PaymentEventStatus status,
    boolean cancellationAllowed) {
  public static PaymentAllocationResponse from(PartnerPaymentEvent event, boolean allowed) {
    return new PaymentAllocationResponse(
        event.getId(),
        event.getParentEvent().getId(),
        event.getTargetType(),
        event.getTargetId(),
        event.getAmount(),
        event.getEventDate(),
        event.getStatus(),
        allowed);
  }
}
