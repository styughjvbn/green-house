package com.greenhouse.backend.sales.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.sales.payment.api.ManualPaymentCommand;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import com.greenhouse.backend.sales.payment.spi.PaymentTargetPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ManualPaymentServiceTest {
  private final PaymentLedgerService ledger = mock(PaymentLedgerService.class);
  private final RequestActorProvider actors = mock(RequestActorProvider.class);
  private final PartnerBalanceService balances = mock(PartnerBalanceService.class);

  @SuppressWarnings("unchecked")
  private final PaymentTargetPort<String> target = mock(PaymentTargetPort.class);

  private final Clock clock = Clock.fixed(Instant.parse("2026-09-05T15:30:00Z"), ZoneOffset.UTC);
  private final ManualPaymentService service =
      new ManualPaymentService(ledger, actors, clock, balances);
  private final ManualPaymentCommand command =
      new ManualPaymentCommand(
          100L, LocalDate.of(2026, 9, 6), "same-key", "계좌이체", null, "worker", null);

  @ParameterizedTest
  @EnumSource(
      value = PaymentTargetType.class,
      names = {"SALES_SLIP", "AUCTION_SETTLEMENT"})
  void paymentLocksItsOwnerBeforeReplayAndAuditsAfterLedgerAndBalance(PaymentTargetType type) {
    when(target.lockAndValidate(7L)).thenReturn(3L);
    when(ledger.findManualPayment(type, 7L, command)).thenReturn(Optional.empty());
    var before = Map.<String, Object>of("paidAmount", 0L);
    when(target.paymentSnapshot(7L)).thenReturn(before);
    when(target.remainingAmount(7L)).thenReturn(100L);
    when(actors.resolve("worker")).thenReturn("authenticated-worker");
    when(ledger.recordManualPayment(3L, type, 7L, command)).thenReturn(9L);
    when(target.response(7L)).thenReturn("current-value");

    assertThat(service.confirm(7L, type, command, target)).isEqualTo("current-value");

    var order = inOrder(target, ledger);
    order.verify(target).lockAndValidate(7L);
    order.verify(ledger).findManualPayment(type, 7L, command);
    order.verify(target).paymentSnapshot(7L);
    order
        .verify(target)
        .recordPayment(7L, 100L, "authenticated-worker", LocalDateTime.of(2026, 9, 5, 15, 30));
    order.verify(ledger).recordManualPayment(3L, type, 7L, command);
    order.verify(target).updateBalance(7L, 9L);
    order.verify(target).auditPayment(7L, before);
    order.verify(target).response(7L);
  }

  @ParameterizedTest
  @EnumSource(
      value = PaymentTargetType.class,
      names = {"SALES_SLIP", "AUCTION_PROCEEDS"})
  void overpaymentAllocatesOnlyRemainingAmountAndRefreshesUnassignedBalance(
      PaymentTargetType type) {
    var overpayment =
        new ManualPaymentCommand(
            120L, LocalDate.of(2026, 9, 6), "over-key", "계좌이체", null, "worker", null);
    when(target.lockAndValidate(7L)).thenReturn(3L);
    when(ledger.findManualPayment(type, 7L, overpayment)).thenReturn(Optional.empty());
    when(target.remainingAmount(7L)).thenReturn(100L);
    when(target.paymentSnapshot(7L)).thenReturn(Map.of("paidAmount", 0L));
    when(actors.resolve("worker")).thenReturn("authenticated-worker");
    when(ledger.recordManualPayment(3L, type, 7L, overpayment, 100L)).thenReturn(9L);
    when(target.response(7L)).thenReturn("fully-paid");

    assertThat(service.confirm(7L, type, overpayment, target)).isEqualTo("fully-paid");

    verify(target)
        .recordPayment(7L, 100L, "authenticated-worker", LocalDateTime.of(2026, 9, 5, 15, 30));
    verify(ledger).recordManualPayment(3L, type, 7L, overpayment, 100L);
    verify(balances).refreshUnassignedAmount(3L, 9L);
  }

  @ParameterizedTest
  @EnumSource(
      value = PaymentTargetType.class,
      names = {"SALES_SLIP", "AUCTION_SETTLEMENT"})
  void replayReturnsCurrentTargetWithoutAnotherLedgerBalanceOrAudit(PaymentTargetType type) {
    when(target.lockAndValidate(7L)).thenReturn(3L);
    when(ledger.findManualPayment(type, 7L, command)).thenReturn(Optional.of(9L));
    when(target.response(7L)).thenReturn("after-later-payment");

    assertThat(service.confirm(7L, type, command, target)).isEqualTo("after-later-payment");

    verify(target, never()).paymentSnapshot(7L);
    verify(target, never()).updateBalance(7L, 9L);
    verify(ledger, never()).recordManualPayment(3L, type, 7L, command);
    verifyNoInteractions(actors);
  }

  @ParameterizedTest
  @EnumSource(
      value = PaymentTargetType.class,
      names = {"SALES_SLIP", "AUCTION_SETTLEMENT"})
  void reusedKeyFailsBeforeTargetAmountsAreTouched(PaymentTargetType type) {
    when(target.lockAndValidate(7L)).thenReturn(3L);
    when(ledger.findManualPayment(type, 7L, command))
        .thenThrow(new ConflictException("IDEMPOTENCY_KEY_REUSED", "changed amount"));

    assertThatThrownBy(() -> service.confirm(7L, type, command, target))
        .isInstanceOf(ConflictException.class);

    verify(target, never()).paymentSnapshot(7L);
    verify(ledger, never()).recordManualPayment(3L, type, 7L, command);
    verifyNoInteractions(actors);
  }
}
