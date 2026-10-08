package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.*;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.application.payment.UnassignedReceiptService;
import com.greenhouse.backend.sales.domain.partner.BusinessPartner;
import com.greenhouse.backend.sales.dto.payment.CancelUnassignedReceiptRequest;
import com.greenhouse.backend.sales.payment.api.ManualPaymentCommand;
import com.greenhouse.backend.sales.repository.partner.BusinessPartnerRepository;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
class UnassignedReceiptPostgresE2ETest extends WorkE2ETestBase {
  @Autowired UnassignedReceiptService receipts;
  @Autowired BusinessPartnerRepository partners;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;
  Long partnerId;

  @BeforeEach
  void seed() {
    partnerId =
        partners
            .saveAndFlush(
                new BusinessPartner(
                    "수납 " + UUID.randomUUID(), PartnerType.WHOLESALE, null, null, null, null))
            .getId();
  }

  @Test
  void simultaneousDuplicateReceiptAndCorrectionHaveOneCashEffect() throws Exception {
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                start.await();
                return receipts.receive(partnerId, command("same"));
              });
      var second =
          pool.submit(
              () -> {
                start.await();
                return receipts.receive(partnerId, command("same"));
              });
      start.countDown();
      var id = first.get(15, TimeUnit.SECONDS).id();
      assertThat(second.get(15, TimeUnit.SECONDS).id()).isEqualTo(id);
      assertThat(cashBalance()).isEqualTo(100);
      var cancelStart = new CountDownLatch(1);
      var cancellation =
          new CancelUnassignedReceiptRequest(LocalDate.of(2026, 10, 8), "중복 입력", "cancel");
      var cancel1 =
          pool.submit(
              () -> {
                cancelStart.await();
                return receipts.cancel(partnerId, id, cancellation);
              });
      var cancel2 =
          pool.submit(
              () -> {
                cancelStart.await();
                return receipts.cancel(partnerId, id, cancellation);
              });
      cancelStart.countDown();
      assertThat(cancel1.get(15, TimeUnit.SECONDS).id())
          .isEqualTo(cancel2.get(15, TimeUnit.SECONDS).id());
      assertThat(cashBalance()).isZero();
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from partner_payment_events where partner_id = ?",
                  Long.class,
                  partnerId))
          .isEqualTo(2);
    }
  }

  @Test
  void rollbackRemovesReceiptBalanceAndAuditTogetherAndSameKeyCanRetry() {
    long auditBefore = jdbc.queryForObject("select count(*) from audit_events", Long.class);
    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactions)
                    .executeWithoutResult(
                        status -> {
                          receipts.receive(partnerId, command("rollback"));
                          throw new IllegalStateException("fail after ledger and summary");
                        }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from partner_payment_events where partner_id = ?",
                Long.class,
                partnerId))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from partner_balance_summaries where partner_id = ?",
                Long.class,
                partnerId))
        .isZero();
    assertThat(jdbc.queryForObject("select count(*) from audit_events", Long.class))
        .isEqualTo(auditBefore);
    receipts.receive(partnerId, command("rollback"));
    assertThat(cashBalance()).isEqualTo(100);
  }

  @Test
  void exceedingBigintBalanceRollsBackCashAndAllowsAnotherValidReceipt() {
    receipts.receive(
        partnerId,
        new ManualPaymentCommand(
            Long.MAX_VALUE, LocalDate.of(2026, 10, 8), "maximum", null, null, null, null));
    assertThatThrownBy(() -> receipts.receive(partnerId, command("overflow")))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("허용 범위");
    assertThat(cashBalance()).isEqualTo(Long.MAX_VALUE);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from partner_payment_events where partner_id = ?",
                Long.class,
                partnerId))
        .isEqualTo(1);
  }

  private long cashBalance() {
    return jdbc.queryForObject(
        "select unapplied_payment_amount from partner_balance_summaries where partner_id = ?",
        Long.class,
        partnerId);
  }

  private ManualPaymentCommand command(String key) {
    return new ManualPaymentCommand(100L, LocalDate.of(2026, 10, 8), key, null, null, null, null);
  }
}
