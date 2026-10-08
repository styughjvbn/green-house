package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.*;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.application.auction.AuctionProceedsReader;
import com.greenhouse.backend.sales.application.auction.AuctionProceedsService;
import com.greenhouse.backend.sales.application.direct.SalesPaymentService;
import com.greenhouse.backend.sales.document.application.SalesSlipStatusService;
import com.greenhouse.backend.sales.document.domain.*;
import com.greenhouse.backend.sales.document.repository.SalesSlipRepository;
import com.greenhouse.backend.sales.document.web.dto.SalesSlipStatusUpdateRequest;
import com.greenhouse.backend.sales.domain.auction.*;
import com.greenhouse.backend.sales.partner.domain.*;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.payment.api.ManualPaymentCommand;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import com.greenhouse.backend.sales.payment.application.*;
import com.greenhouse.backend.sales.payment.web.dto.*;
import com.greenhouse.backend.sales.repository.auction.AuctionShipmentRepository;
import com.greenhouse.backend.support.DirectSaleFixtures;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
class MultiPaymentAllocationPostgresE2ETest extends WorkE2ETestBase {
  static final LocalDate DATE = LocalDate.of(2026, 10, 8);
  @Autowired PaymentAllocationService allocations;
  @Autowired AuctionProceedsService proceeds;
  @Autowired AuctionProceedsReader proceedsReader;
  @Autowired AuctionShipmentRepository shipments;
  @Autowired SalesPaymentService manual;
  @Autowired SalesSlipStatusService statuses;
  @Autowired UnassignedReceiptService receipts;
  @Autowired PaymentReceiptReader reader;
  @Autowired SalesSlipRepository documents;
  @Autowired BusinessPartnerRepository partners;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;
  Long partnerId, first, second;

  @BeforeEach
  void seed() {
    partnerId =
        partners
            .saveAndFlush(
                new BusinessPartner(
                    "배분 " + UUID.randomUUID(), PartnerType.WHOLESALE, null, null, null, null))
            .getId();
    var tx = new TransactionTemplate(transactions);
    first = tx.execute(status -> document());
    second = tx.execute(status -> document());
  }

  @Test
  void splitCombineAndCorrectionPreserveOriginalCashAndExactReplay() {
    long a = receive(700), b = receive(300);
    var request = command("split", line(a, first, 400), line(a, second, 300), line(b, first, 300));
    var original = allocations.allocate(partnerId, request);
    var correction =
        new PaymentAllocationCorrectionRequest(
            DATE,
            original.allocationIds(),
            List.of(line(a, second, 700), line(b, second, 300)),
            "대상 변경",
            "correct",
            null);
    var result = allocations.correct(partnerId, correction);
    assertThat(allocations.correct(partnerId, correction)).isEqualTo(result);
    assertThat(allocations.allocate(partnerId, request)).isEqualTo(original);
    assertThat(paid(first)).isZero();
    assertThat(paid(second)).isEqualTo(1000);
    assertThat(reader.get(partnerId, a).reviewRequired()).isFalse();
    assertThat(reader.get(partnerId, b).availableAmount()).isZero();
    assertThat(count("event_type = 'PAYMENT_RECEIVED'")).isEqualTo(2);
  }

  @Test
  void failedReplacementRollsBackCancellationCashSummaryAndCommandReceipt() {
    long source = receive(700);
    var original = allocations.allocate(partnerId, command("initial", line(source, first, 700)));
    var invalid =
        new PaymentAllocationCorrectionRequest(
            DATE,
            original.allocationIds(),
            List.of(line(source, second, 800)),
            "금액 오류",
            "retry",
            null);
    assertThatThrownBy(() -> allocations.correct(partnerId, invalid))
        .isInstanceOf(ConflictException.class);
    assertThat(paid(first)).isEqualTo(700);
    assertThat(paid(second)).isZero();
    assertThat(reader.get(partnerId, source).availableAmount()).isZero();
    assertThat(
            reader.allocations(partnerId, source, 0, 10).content().getFirst().cancellationAllowed())
        .isTrue();
    assertThat(count("event_type = 'PAYMENT_UNLINKED'")).isZero();
    var valid =
        new PaymentAllocationCorrectionRequest(
            DATE,
            original.allocationIds(),
            List.of(line(source, second, 700)),
            "금액 오류",
            "retry",
            null);
    allocations.correct(partnerId, valid);
    assertThat(paid(first)).isZero();
    assertThat(paid(second)).isEqualTo(700);
  }

  @Test
  void outerFailureRollsBackLedgerProjectionAuditAndIdempotencyTogether() {
    long source = receive(500);
    long audit = jdbc.queryForObject("select count(*) from audit_events", Long.class);
    var command = command("rollback", line(source, first, 500));
    assertThatThrownBy(
            () ->
                new TransactionTemplate(transactions)
                    .executeWithoutResult(
                        status -> {
                          allocations.allocate(partnerId, command);
                          throw new IllegalStateException("after summaries");
                        }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(paid(first)).isZero();
    assertThat(reader.get(partnerId, source).availableAmount()).isEqualTo(500);
    assertThat(count("event_type = 'PAYMENT_ALLOCATED'")).isZero();
    assertThat(jdbc.queryForObject("select count(*) from audit_events", Long.class))
        .isEqualTo(audit);
    allocations.allocate(partnerId, command);
    assertThat(paid(first)).isEqualTo(500);
  }

  @Test
  void concurrentSameKeyHasExactlyOneEffect() throws Exception {
    long source = receive(700);
    var command = command("same", line(source, first, 700));
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                start.await();
                return allocations.allocate(partnerId, command);
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                return allocations.allocate(partnerId, command);
              });
      start.countDown();
      assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo(b.get(20, TimeUnit.SECONDS));
    }
    assertThat(count("event_type = 'PAYMENT_ALLOCATED'")).isEqualTo(1);
    assertThat(paid(first)).isEqualTo(700);
  }

  @Test
  void concurrentCommandsCannotSpendReceiptTwice() throws Exception {
    long source = receive(700);
    compete(command("a", line(source, first, 700)), command("b", line(source, second, 700)));
    assertThat(paid(first) + paid(second)).isEqualTo(700);
    assertThat(reader.get(partnerId, source).availableAmount()).isZero();
  }

  @Test
  void concurrentReceiptsCannotExceedTargetAmount() throws Exception {
    long a = receive(700), b = receive(700);
    compete(command("a", line(a, first, 700)), command("b", line(b, first, 700)));
    assertThat(paid(first)).isEqualTo(700);
    assertThat(
            reader.get(partnerId, a).availableAmount() + reader.get(partnerId, b).availableAmount())
        .isEqualTo(700);
  }

  @Test
  void foreignPartnerTargetAndCorruptReceiptAreRejectedWithoutWrites() {
    long source = receive(500);
    long foreign =
        partners
            .saveAndFlush(
                new BusinessPartner("다른 거래처", PartnerType.WHOLESALE, null, null, null, null))
            .getId();
    jdbc.update("update sales_slips set partner_id = ? where id = ?", foreign, second);
    assertThatThrownBy(
            () -> allocations.allocate(partnerId, command("foreign", line(source, second, 500))))
        .isInstanceOf(RuntimeException.class);
    jdbc.update("update partner_payment_events set unapplied_amount = 400 where id = ?", source);
    assertThat(reader.get(partnerId, source).reviewRequired()).isTrue();
    assertThatThrownBy(
            () -> allocations.allocate(partnerId, command("corrupt", line(source, first, 100))))
        .isInstanceOf(RuntimeException.class);
    assertThat(count("event_type = 'PAYMENT_ALLOCATED'")).isZero();
  }

  @Test
  void oneReceiptCanBeSplitBetweenDirectAndConfirmedAuctionTargets() {
    jdbc.update(
        "update business_partners set partner_type = 'AUCTION_HOUSE' where id = ?", partnerId);
    var shipment = new AuctionShipment(DATE, partnerId, PartnerType.AUCTION_HOUSE);
    var lot = new AuctionShipmentLot("출하 품목", "품종", null, null, 1);
    var attempt = new AuctionAttempt(DATE, 1, AuctionAttemptStatus.SOLD, null, null);
    var result =
        new AuctionResultLine(DATE, null, 1, 1000, 1000, null, AuctionInspectionStatus.NORMAL);
    attempt.addResultLine(result);
    lot.addAttempt(attempt);
    shipment.addLot(lot);
    shipments.saveAndFlush(shipment);
    long target = proceeds.record(partnerId, "근거", 1000L, 930L, List.of(result.getId()));
    proceeds.confirm(target, "확인자");
    long source = receive(1500);
    var original =
        allocations.allocate(
            partnerId,
            command(
                "mixed",
                line(source, first, 570),
                new PaymentAllocationLine(
                    source, PaymentTargetType.AUCTION_PROCEEDS, target, 930L)));
    assertThat(paid(first)).isEqualTo(570);
    assertThat(proceedsReader.get(target).paidAmount()).isEqualByComparingTo("930");
    allocations.correct(
        partnerId,
        new PaymentAllocationCorrectionRequest(
            DATE,
            original.allocationIds(),
            List.of(line(source, first, 1000)),
            "혼합 배분 정정",
            "mixed-correct",
            null));
    assertThat(proceedsReader.get(target).paidAmount()).isZero();
    assertThat(reader.get(partnerId, source).availableAmount()).isEqualTo(500);
    assertThat(reader.get(partnerId, source).reviewRequired()).isFalse();
  }

  @Test
  void allocationAndLegacyPaymentShareTargetCapacity() throws Exception {
    long source = receive(700);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                start.await();
                return attempt(command("allocation", line(source, first, 700)));
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                try {
                  manual.confirmPayment(
                      first,
                      new ManualPaymentCommand(700L, DATE, "legacy", null, null, null, null));
                  return true;
                } catch (IllegalArgumentException expected) {
                  return false;
                }
              });
      start.countDown();
      assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(paid(first)).isEqualTo(700);
  }

  @Test
  void allocationAndDocumentCancellationCannotBothCommit() throws Exception {
    long source = receive(500);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                start.await();
                return attempt(command("allocation", line(source, first, 500)));
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                try {
                  statuses.updateStatus(first, new SalesSlipStatusUpdateRequest("취소", null));
                  return true;
                } catch (IllegalArgumentException expected) {
                  return false;
                }
              });
      start.countDown();
      assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    if (paid(first) > 0)
      assertThat(
              jdbc.queryForObject(
                  "select sales_status from sales_slips where id = ?", String.class, first))
          .isEqualTo("작성중");
    else assertThat(reader.get(partnerId, source).availableAmount()).isEqualTo(500);
  }

  private void compete(PaymentAllocationRequest a, PaymentAllocationRequest b) throws Exception {
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var one =
          pool.submit(
              () -> {
                start.await();
                return attempt(a);
              });
      var two =
          pool.submit(
              () -> {
                start.await();
                return attempt(b);
              });
      start.countDown();
      assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
  }

  private boolean attempt(PaymentAllocationRequest request) {
    try {
      allocations.allocate(partnerId, request);
      return true;
    } catch (IllegalArgumentException | ConflictException expected) {
      return false;
    }
  }

  private long document() {
    var slip =
        new SalesSlip(
            "multi-" + UUID.randomUUID(),
            DATE,
            SalesType.DIRECT,
            null,
            partnerId,
            "미입금",
            "작성중",
            "계좌이체",
            null);
    slip.addItem(new SalesSlipItem(null, "난", null, "A", 1, 1000, null));
    DirectSaleFixtures.refreshProjection(slip);
    documents.saveAndFlush(slip);
    DirectSaleFixtures.copyTerms(jdbc, slip.getId());
    return slip.getId();
  }

  private long receive(long amount) {
    return receipts
        .receive(
            partnerId,
            new ManualPaymentCommand(
                amount, DATE, UUID.randomUUID().toString(), null, null, null, null))
        .id();
  }

  private PaymentAllocationLine line(long source, long target, long amount) {
    return new PaymentAllocationLine(source, PaymentTargetType.SALES_SLIP, target, amount);
  }

  private PaymentAllocationRequest command(String key, PaymentAllocationLine... lines) {
    return new PaymentAllocationRequest(DATE, List.of(lines), key, null);
  }

  private long paid(long target) {
    return jdbc.queryForObject(
        "select paid_amount from sales_slips where id = ?", Long.class, target);
  }

  private long count(String condition) {
    return jdbc.queryForObject(
        "select count(*) from partner_payment_events where partner_id = ? and " + condition,
        Long.class,
        partnerId);
  }
}
