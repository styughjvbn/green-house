package com.greenhouse.backend.sales.application.payment;

import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.sales.domain.payment.PartnerPaymentEvent;
import com.greenhouse.backend.sales.domain.payment.PaymentTargetType;
import com.greenhouse.backend.sales.repository.payment.PartnerPaymentEventRepository;
import java.util.Objects;
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

  private final PaymentAuditSupport auditSupport;

  public boolean isTargetMigrated(PaymentTargetType type, Long id) {
    return eventRepository.isTargetMigrated(type.name(), id);
  }

  public Long recordManualPayment(
      Long partnerId, PaymentTargetType targetType, Long targetId, ManualPaymentCommand request) {
    var received =
        eventRepository.save(
            PartnerPaymentEvent.received(
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
    return received.getId();
  }

  public Optional<Long> findManualPayment(
      PaymentTargetType targetType, Long targetId, ManualPaymentCommand request) {
    var event =
        eventRepository.findByExternalUid(
            externalUid(targetType, targetId, request.idempotencyKey()));
    if (event.isEmpty() && targetType == PaymentTargetType.AUCTION_PROCEEDS) {
      var original = eventRepository.findOriginalTarget(targetType.name(), targetId);
      if (original.isPresent()) {
        var alias = original.orElseThrow();
        event =
            eventRepository.findByExternalUid(
                externalUid(
                    PaymentTargetType.valueOf(alias.getTargetType()),
                    alias.getTargetId(),
                    request.idempotencyKey()));
      }
    }
    return event.map(
        received -> {
          if (targetType == PaymentTargetType.AUCTION_PROCEEDS
              && (received.getTargetType() != targetType
                  || !Objects.equals(received.getTargetId(), targetId)))
            throw new ConflictException(
                "PAYMENT_REPLAY_TARGET_MISMATCH", "기존 입금의 대상 연결을 확인해야 합니다.");
          received.validateReplay(request.amount(), request.paymentDate());
          return received.getId();
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
