package com.greenhouse.backend.sales.payment.web.dto;

import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import com.greenhouse.backend.sales.payment.domain.PartnerPaymentEvent;
import com.greenhouse.backend.sales.payment.domain.PaymentEventStatus;
import java.time.LocalDate;

public record PaymentReceiptResponse(
    Long id,
    Long partnerId,
    LocalDate paymentDate,
    Long amount,
    Long availableAmount,
    PaymentEventStatus status,
    @io.swagger.v3.oas.annotations.media.Schema(nullable = true)
        PaymentTargetType originalTargetType,
    @io.swagger.v3.oas.annotations.media.Schema(nullable = true) Long originalTargetId,
    boolean allocationAllowed,
    boolean correctionAllowed,
    boolean reviewRequired,
    @io.swagger.v3.oas.annotations.media.Schema(nullable = true) String depositorName,
    @io.swagger.v3.oas.annotations.media.Schema(nullable = true) String memo) {
  public static PaymentReceiptResponse from(
      PartnerPaymentEvent event, long available, boolean review) {
    return new PaymentReceiptResponse(
        event.getId(),
        event.getPartnerId(),
        event.getEventDate(),
        event.getAmount(),
        available,
        event.getStatus(),
        event.getTargetType(),
        event.getTargetId(),
        event.isReceiptAllocationAllowed(review),
        event.isReceiptCorrectionAllowed(review),
        review,
        event.getDepositorName(),
        event.getMemo());
  }
}
