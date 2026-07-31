package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.common.application.RequestActorProvider;
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
	private final RequestActorProvider requestActorProvider;

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
				defaultWorker(requestActorProvider.resolve(request.worker()))));
		eventRepository.save(PartnerPaymentEvent.manualMatch(received));
		return received;
	}

	private String defaultWorker(String value) {
		return value == null ? "관리자" : value;
	}

	private String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
