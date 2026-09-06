package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.settlement.domain.PartnerPaymentEvent;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import com.greenhouse.backend.settlement.repository.PartnerPaymentEventRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
public class PaymentLedgerService {
	private final PartnerPaymentEventRepository eventRepository;
	private final RequestActorProvider requestActorProvider;
	private final SettlementAuditSupport auditSupport;

	public PaymentReceipt recordManualPayment(
			Long partnerId,
			PaymentTargetType targetType,
			Long targetId,
			ManualPaymentCommand request) {
		var received = eventRepository.save(PartnerPaymentEvent.received(
				partnerId,
				request.paymentDate(),
				request.amount(),
				targetType,
				targetId,
				normalize(request.paymentMethod()),
				normalize(request.depositorName()),
				externalUid(targetType, targetId, request.idempotencyKey()),
				normalize(request.memo()),
				defaultWorker(requestActorProvider.resolve(request.worker()))));
		eventRepository.save(PartnerPaymentEvent.manualMatch(received));
		auditSupport.recordManualPayment(received);
		return new PaymentReceipt(received.getId());
	}

	public Optional<PaymentReceipt> findManualPayment(
			PaymentTargetType targetType,
			Long targetId,
			ManualPaymentCommand request) {
		return eventRepository.findByExternalUid(externalUid(targetType, targetId, request.idempotencyKey()))
				.map(event -> {
					event.validateReplay(request.amount(), request.paymentDate());
					return new PaymentReceipt(event.getId());
				});
	}

	private String externalUid(PaymentTargetType targetType, Long targetId, String idempotencyKey) {
		return "MANUAL:" + targetType.name() + ":" + targetId + ":" + idempotencyKey.trim();
	}

	private String defaultWorker(String value) {
		return value == null ? "관리자" : value;
	}

	private String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
