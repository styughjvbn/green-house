package com.greenhouse.backend.sales.payment.application;

import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.sales.payment.api.ManualPaymentApi;
import com.greenhouse.backend.sales.payment.api.ManualPaymentCommand;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import com.greenhouse.backend.sales.payment.spi.PaymentTargetPort;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ManualPaymentService implements ManualPaymentApi {
  private final PaymentLedgerService ledger;
  private final RequestActorProvider actors;
  private final Clock clock;

  public <T> T confirm(
      Long id, PaymentTargetType type, ManualPaymentCommand command, PaymentTargetPort<T> target) {
    Long partnerId = target.lockAndValidate(id);
    if (ledger.findManualPayment(type, id, command).isPresent()) {
      return target.response(id);
    }
    var before = target.paymentSnapshot(id);
    String worker = actors.resolve(command.worker());
    target.recordPayment(
        id, command.amount(), worker == null ? "관리자" : worker, TimeConfig.utcNow(clock));
    Long eventId = ledger.recordManualPayment(partnerId, type, id, command);
    target.updateBalance(id, eventId);
    target.auditPayment(id, before);
    return target.response(id);
  }
}
