package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.settlement.domain.PartnerPaymentEvent;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import com.greenhouse.backend.settlement.dto.ManualPaymentRequest;
import com.greenhouse.backend.settlement.repository.PartnerPaymentEventRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentLedgerService {
	private final PartnerPaymentEventRepository eventRepository;

	public PartnerPaymentEvent recordManualPayment(
			BusinessPartner partner,
			PaymentTargetType targetType,
			Long targetId,
			ManualPaymentRequest request) {
		var received = eventRepository.save(PartnerPaymentEvent.received(
				partner,
				request.paymentDate(),
				request.amount(),
				targetType,
				targetId,
				normalize(request.paymentMethod()),
				normalize(request.depositorName()),
				normalize(request.memo()),
				worker(request.worker())));
		eventRepository.save(PartnerPaymentEvent.manualMatch(received));
		return received;
	}

	private String worker(String value) {
		String normalized = normalize(value);
		return normalized == null ? "관리자" : normalized;
	}

	private String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
