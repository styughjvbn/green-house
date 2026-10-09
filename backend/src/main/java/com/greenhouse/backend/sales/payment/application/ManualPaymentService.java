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
  private final PartnerBalanceService balances;

  public <T> T confirm(
      Long id, PaymentTargetType type, ManualPaymentCommand command, PaymentTargetPort<T> target) {
    return receiveAndAllocate(id, type, command, command.amount(), target);
  }

  public <T> T receiveAndAllocate(
      Long id,
      PaymentTargetType type,
      ManualPaymentCommand command,
      long allocationAmount,
      PaymentTargetPort<T> target) {
    if (command.amount() == null
        || command.amount() <= 0
        || allocationAmount < 0
        || allocationAmount > command.amount())
      throw new IllegalArgumentException("수납액은 양수이고 배분액은 수납액 이하여야 합니다.");
    Long partnerId = target.lockAndValidate(id);
    if (ledger.findManualPayment(type, id, command).isPresent()) {
      return target.response(id);
    }
    var before = target.paymentSnapshot(id);
    String worker = actors.resolve(command.worker());
    if (allocationAmount > 0)
      target.recordPayment(
          id, allocationAmount, worker == null ? "관리자" : worker, TimeConfig.utcNow(clock));
    Long eventId =
        allocationAmount == command.amount()
            ? ledger.recordManualPayment(partnerId, type, id, command)
            : ledger.recordManualPayment(partnerId, type, id, command, allocationAmount);
    target.updateBalance(id, eventId);
    if (allocationAmount < command.amount()) balances.refreshUnassignedAmount(partnerId, eventId);
    target.auditPayment(id, before);
    return target.response(id);
  }
}
