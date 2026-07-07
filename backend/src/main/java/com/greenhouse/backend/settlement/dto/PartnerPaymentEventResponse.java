package com.greenhouse.backend.settlement.dto;

import com.greenhouse.backend.settlement.domain.PartnerPaymentEvent;
import com.greenhouse.backend.settlement.domain.PaymentEventStatus;
import com.greenhouse.backend.settlement.domain.PaymentEventType;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
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
	public static PartnerPaymentEventResponse from(PartnerPaymentEvent event) {
		return new PartnerPaymentEventResponse(
				event.getId(), event.getPartner().getId(), event.getPartner().getName(), event.getEventType(),
				event.getEventDate(), event.getAmount(), event.getUnappliedAmount(), event.getTargetType(),
				event.getTargetId(), event.getParentEvent() == null ? null : event.getParentEvent().getId(),
				event.getPaymentMethod(), event.getDepositorName(), event.getDescription(), event.getStatus(),
				event.getMemo(), event.getCreatedBy());
	}
}
