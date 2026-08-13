package com.greenhouse.backend.settlement.application;

import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.settlement.domain.PartnerPaymentEvent;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import com.greenhouse.backend.settlement.dto.ManualPaymentRequest;
import com.greenhouse.backend.settlement.repository.PartnerPaymentEventRepository;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentLedgerService {
	private final PartnerPaymentEventRepository eventRepository;
	private final RequestActorProvider requestActorProvider;
	private final SettlementAuditSupport auditSupport;

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
				externalUid(targetType, targetId, request.idempotencyKey()),
				normalize(request.memo()),
				defaultWorker(requestActorProvider.resolve(request.worker()))));
		eventRepository.save(PartnerPaymentEvent.manualMatch(received));
		auditSupport.recordManualPayment(received);
		return received;
	}

	public Optional<PartnerPaymentEvent> findManualPayment(
			PaymentTargetType targetType,
			Long targetId,
			ManualPaymentRequest request) {
		return eventRepository.findByExternalUid(externalUid(targetType, targetId, request.idempotencyKey()))
				.map(event -> validateReplay(event, request));
	}

	private PartnerPaymentEvent validateReplay(PartnerPaymentEvent event, ManualPaymentRequest request) {
		if (!Objects.equals(event.getAmount(), request.amount())
				|| !Objects.equals(event.getEventDate(), request.paymentDate())) {
			throw new IllegalArgumentException("같은 입금 멱등 키를 다른 금액 또는 입금일에 재사용할 수 없습니다.");
		}
		return event;
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
