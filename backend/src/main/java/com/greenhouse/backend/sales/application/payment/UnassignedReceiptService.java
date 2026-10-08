package com.greenhouse.backend.sales.application.payment;

import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.application.partner.BusinessPartnerLock;
import com.greenhouse.backend.sales.domain.payment.PartnerPaymentEvent;
import com.greenhouse.backend.sales.dto.payment.CancelUnassignedReceiptRequest;
import com.greenhouse.backend.sales.dto.payment.PartnerPaymentEventResponse;
import com.greenhouse.backend.sales.repository.payment.PartnerPaymentEventRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Partner lock serializes receipt, correction and the partner's cash balance. */
@Service
@RequiredArgsConstructor
@Transactional
public class UnassignedReceiptService {
  private final PartnerPaymentEventRepository events;
  private final BusinessPartnerLock partners;
  private final PartnerBalanceService balances;
  private final RequestActorProvider actors;
  private final PaymentAuditSupport audit;
  private final PaymentReceiptIntegrity cash;

  public PartnerPaymentEventResponse receive(Long partnerId, ManualPaymentCommand command) {
    var partner = partners.lockAll(List.of(partnerId)).getFirst();
    String uid = "UNASSIGNED:" + partnerId + ":" + command.idempotencyKey().trim();
    var existing = events.findByExternalUid(uid);
    if (existing.isPresent()) {
      var receipt = existing.orElseThrow();
      receipt.validateUnassignedReplay(
          command.amount(),
          command.paymentDate(),
          normalize(command.paymentMethod()),
          normalize(command.depositorName()),
          normalize(command.memo()));
      return PartnerPaymentEventResponse.from(receipt, partner.name());
    }
    var receipt =
        events.save(
            PartnerPaymentEvent.unassignedReceipt(
                partnerId,
                command.paymentDate(),
                command.amount(),
                normalize(command.paymentMethod()),
                normalize(command.depositorName()),
                uid,
                normalize(command.memo()),
                actor(command.worker())));
    balances.refreshUnassignedAmount(partnerId, receipt.getId());
    audit.recordManualPayment(receipt);
    return PartnerPaymentEventResponse.from(receipt, partner.name());
  }

  public PartnerPaymentEventResponse cancel(
      Long partnerId, Long receiptId, CancelUnassignedReceiptRequest command) {
    var partner = partners.lockAll(List.of(partnerId)).getFirst();
    String uid = "UNASSIGNED_CANCEL:" + partnerId + ":" + command.idempotencyKey().trim();
    var existing = events.findByExternalUid(uid);
    if (existing.isPresent()) {
      var correction = existing.orElseThrow();
      correction.validateCancellationReplay(
          receiptId, command.correctionDate(), command.reason().trim());
      return PartnerPaymentEventResponse.from(correction, partner.name());
    }
    var receipt =
        events
            .findById(receiptId)
            .filter(event -> partnerId.equals(event.getPartnerId()))
            .orElseThrow(() -> new NotFoundException("거래처의 수납을 찾을 수 없습니다."));
    var state = cash.findAll(List.of(receiptId)).get(receiptId);
    if (state == null || state.reviewRequired()) {
      throw new ConflictException(
          "PAYMENT_RECEIPT_CANCELLATION_BLOCKED", "수납과 배분 이력을 먼저 확인하거나 정정해야 합니다.");
    }
    if (events.hasActiveAllocations(receiptId)) {
      throw new ConflictException(
          "PAYMENT_RECEIPT_CANCELLATION_BLOCKED", "후속 기록이 있는 수납은 먼저 배분을 정정해야 합니다.");
    }
    var correction =
        events.save(
            receipt.cancelUnassignedReceipt(
                command.correctionDate(), command.reason().trim(), actor(null), uid));
    balances.refreshUnassignedAmount(partnerId, correction.getId());
    audit.recordManualPayment(correction);
    return PartnerPaymentEventResponse.from(correction, partner.name());
  }

  private String actor(String requested) {
    String actor = actors.resolve(requested);
    return actor == null ? "관리자" : actor;
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
