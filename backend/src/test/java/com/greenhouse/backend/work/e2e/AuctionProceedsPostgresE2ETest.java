package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.sales.application.auction.AuctionProceedsPaymentService;
import com.greenhouse.backend.sales.application.auction.AuctionProceedsReader;
import com.greenhouse.backend.sales.application.auction.AuctionProceedsService;
import com.greenhouse.backend.sales.application.payment.ManualPaymentCommand;
import com.greenhouse.backend.sales.domain.auction.*;
import com.greenhouse.backend.sales.domain.partner.BusinessPartner;
import com.greenhouse.backend.sales.domain.partner.PartnerType;
import com.greenhouse.backend.sales.domain.payment.PartnerPaymentEvent;
import com.greenhouse.backend.sales.domain.payment.PaymentTargetType;
import com.greenhouse.backend.sales.repository.auction.AuctionProceedsRepository;
import com.greenhouse.backend.sales.repository.auction.AuctionShipmentRepository;
import com.greenhouse.backend.sales.repository.partner.BusinessPartnerRepository;
import com.greenhouse.backend.sales.repository.payment.PartnerPaymentEventRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
class AuctionProceedsPostgresE2ETest extends WorkE2ETestBase {
  @Autowired AuctionProceedsService service;
  @Autowired AuctionProceedsReader reader;
  @Autowired AuctionProceedsPaymentService payments;
  @Autowired PartnerPaymentEventRepository events;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManagerFactory emf;
  @Autowired AuctionProceedsRepository proceeds;
  @Autowired AuctionShipmentRepository shipments;
  @Autowired BusinessPartnerRepository partners;

  @Test
  void persistsSuppliedAmountsAndBlocksDuplicateResultOwnership() {
    var fixture = seed(AuctionInspectionStatus.NORMAL);
    Long id =
        service.record(fixture.houseId(), "경매장 결과 자료", 1000L, 930L, List.of(fixture.resultId()));
    assertThat(proceeds.findById(id).orElseThrow().isPaymentTargetReady()).isFalse();
    service.confirm(id, "확인자");
    var confirmed = proceeds.findById(id).orElseThrow();
    assertThat(confirmed.isPaymentTargetReady()).isTrue();
    assertThat(confirmed.getReceivableAmount()).isEqualTo(930);
    assertThatThrownBy(
            () ->
                service.record(
                    fixture.houseId(), "중복 자료", 1000L, 930L, List.of(fixture.resultId())))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(proceeds.findById(id).orElseThrow().isPaymentTargetReady()).isTrue();
  }

  @Test
  void preservesUnconfirmedEvidenceAndRollsBackFailedConfirmation() {
    var fixture = seed(AuctionInspectionStatus.MANUAL_REVIEW);
    Long id = service.record(fixture.houseId(), "검토할 자료", 1000L, null, List.of(fixture.resultId()));
    assertThatThrownBy(() -> service.confirm(id, "확인자")).isInstanceOf(ConflictException.class);
    var stored = proceeds.findById(id).orElseThrow();
    assertThat(stored.isMatchingConfirmed()).isFalse();
    assertThat(stored.getReceivableAmount()).isNull();
    assertThat(stored.getReportedGrossAmount()).isEqualTo(1000);
  }

  @Test
  void rejectsAnotherMarketsResultsWithoutSavingATarget() {
    var fixture = seed(AuctionInspectionStatus.NORMAL);
    var other = seed(AuctionInspectionStatus.NORMAL);
    long before = proceeds.count();
    assertThatThrownBy(
            () ->
                service.record(other.houseId(), "다른 경매장", 1000L, 930L, List.of(fixture.resultId())))
        .isInstanceOf(ConflictException.class);
    assertThat(proceeds.count()).isEqualTo(before);
  }

  @Test
  void paymentUsesValidLinksAndReplaysWithoutAnotherReceipt() throws Exception {
    var fixture = seed(AuctionInspectionStatus.NORMAL);
    Long id = service.record(fixture.houseId(), "입금 자료", 1000L, 930L, List.of(fixture.resultId()));
    service.confirm(id, "확인자");
    var first = payments.confirm(id, command("first", 400));
    assertThat(first.paidAmount()).isEqualByComparingTo("400");
    assertThat(first.remainingAmount()).isEqualByComparingTo("530");
    assertThat(first.paymentAllowed()).isTrue();
    var replay = payments.confirm(id, command("first", 400));
    assertThat(replay).isEqualTo(first);
    assertThatThrownBy(() -> payments.confirm(id, command("first", 401)))
        .isInstanceOf(ConflictException.class);
    var last = payments.confirm(id, command("last", 530));
    assertThat(last.paidAmount()).isEqualByComparingTo("930");
    assertThat(last.remainingAmount()).isEqualByComparingTo("0");
    assertThat(last.paymentAllowed()).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from partner_payment_events where target_type = 'AUCTION_PROCEEDS' and target_id = ?",
                Long.class,
                id))
        .isEqualTo(4);
    assertThat(get("/api/auction-proceeds/" + id).data().path("paidAmount").asLong())
        .isEqualTo(930);
    assertThat(
            get("/api/auction-proceeds/page?auctionHouseId=" + fixture.houseId())
                .data()
                .path("totalElements")
                .asLong())
        .isEqualTo(1);
    assertThatThrownBy(() -> payments.confirm(id, command("excess", 1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(payments.confirm(id, command("first", 400)).paidAmount())
        .isEqualByComparingTo("930");
  }

  @Test
  void grossOnlyAndUnconfirmedTargetsDoNotInventReceivablesOrCash() {
    var fixture = seed(AuctionInspectionStatus.NORMAL);
    Long id =
        service.record(fixture.houseId(), "경락 자료만 있음", 1000L, null, List.of(fixture.resultId()));
    assertThat(reader.get(id).remainingAmount()).isNull();
    assertThatThrownBy(() -> payments.confirm(id, command("not-ready", 1)))
        .isInstanceOf(ConflictException.class);
    service.confirm(id, "확인자");
    assertThat(reader.get(id).paymentAllowed()).isFalse();
    assertThat(reader.get(id).receivableAmount()).isNull();
    assertThatThrownBy(() -> payments.confirm(id, command("still-not-ready", 1)))
        .isInstanceOf(ConflictException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from partner_payment_events where target_type = 'AUCTION_PROCEEDS' and target_id = ?",
                Long.class,
                id))
        .isZero();
  }

  @Test
  void concurrentPaymentsSerializeTheRemainingAmount() throws Exception {
    var fixture = seed(AuctionInspectionStatus.NORMAL);
    Long id = service.record(fixture.houseId(), "동시 입금", 1000L, 930L, List.of(fixture.resultId()));
    service.confirm(id, "확인자");
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var tasks =
          IntStream.range(0, 2)
              .mapToObj(
                  i ->
                      pool.submit(
                          () -> {
                            ready.countDown();
                            start.await();
                            try {
                              payments.confirm(id, command("parallel-" + i, 600));
                              return true;
                            } catch (IllegalArgumentException expected) {
                              return false;
                            }
                          }))
              .toList();
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(tasks.get(0).get(), tasks.get(1).get()))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(reader.get(id).paidAmount()).isEqualByComparingTo("600");
    assertThat(reader.get(id).remainingAmount()).isEqualByComparingTo("330");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from partner_payment_events where target_type = 'AUCTION_PROCEEDS' and target_id = ?",
                Long.class,
                id))
        .isEqualTo(2);
  }

  @Test
  void rootPageLoadsReferencesAndAllocationsInFixedQueries() {
    for (int i = 0; i < 9; i++) {
      var fixture = seed(AuctionInspectionStatus.NORMAL);
      service.record(fixture.houseId(), "조회 " + i, 1000L, 930L, List.of(fixture.resultId()));
    }
    var statistics = emf.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    try {
      statistics.clear();
      var one = reader.page(null, 0, 1);
      long oneCount = statistics.getPrepareStatementCount();
      statistics.clear();
      var eight = reader.page(null, 0, 8);
      assertThat(eight.content()).hasSize(8);
      assertThat(eight.content()).allSatisfy(value -> assertThat(value.resultIds()).hasSize(1));
      assertThat(eight.content())
          .allSatisfy(
              value ->
                  assertThat(value.resultDetails())
                      .singleElement()
                      .satisfies(result -> assertThat(result.amount()).isEqualTo(1000)));
      assertThat(statistics.getPrepareStatementCount()).isEqualTo(oneCount).isLessThanOrEqualTo(6);
    } finally {
      statistics.setStatisticsEnabled(false);
    }
  }

  @Test
  void downstreamAuditFailureRollsBackCashAndBalanceActivity() {
    var fixture = seed(AuctionInspectionStatus.NORMAL);
    Long id = service.record(fixture.houseId(), "원자적 입금", 1000L, 930L, List.of(fixture.resultId()));
    service.confirm(id, "확인자");
    jdbc.execute(
        "CREATE FUNCTION reject_proceeds_test_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.entity_type = 'AUCTION_PROCEEDS' THEN RAISE EXCEPTION 'test audit failure'; END IF; RETURN NEW; END $$");
    jdbc.execute(
        "CREATE TRIGGER reject_proceeds_test_audit BEFORE INSERT ON audit_events FOR EACH ROW EXECUTE FUNCTION reject_proceeds_test_audit()");
    try {
      assertThatThrownBy(() -> payments.confirm(id, command("retry-after-rollback", 400)))
          .isInstanceOf(DataAccessException.class);
      assertThat(reader.get(id).paidAmount()).isEqualByComparingTo("0");
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from partner_payment_events where target_type = 'AUCTION_PROCEEDS' and target_id = ?",
                  Long.class,
                  id))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from partner_balance_summaries where partner_id = ?",
                  Long.class,
                  fixture.houseId()))
          .isZero();
    } finally {
      jdbc.execute("DROP TRIGGER reject_proceeds_test_audit ON audit_events");
      jdbc.execute("DROP FUNCTION reject_proceeds_test_audit()");
    }
    assertThat(payments.confirm(id, command("retry-after-rollback", 400)).paidAmount())
        .isEqualByComparingTo("400");
  }

  @Test
  void migratedCashReplaysItsOriginalUidBeforeReadinessValidation() {
    var fixture = seed(AuctionInspectionStatus.NORMAL);
    Long id = service.record(fixture.houseId(), null, null, null, List.of(fixture.resultId()));
    jdbc.update(
        "insert into payment_target_aliases (original_target_type, original_target_id, target_type, target_id) values ('AUCTION_SETTLEMENT', 999901, 'AUCTION_PROCEEDS', ?)",
        id);
    var received =
        events.saveAndFlush(
            PartnerPaymentEvent.received(
                fixture.houseId(),
                LocalDate.of(2026, 10, 7),
                400L,
                PaymentTargetType.AUCTION_PROCEEDS,
                id,
                "현금",
                null,
                "MANUAL:AUCTION_SETTLEMENT:999901:original",
                null,
                "기존 담당자"));
    events.saveAndFlush(PartnerPaymentEvent.manualMatch(received));
    var before =
        jdbc.queryForList(
            "select * from partner_payment_events where target_type='AUCTION_PROCEEDS' and target_id=? order by id",
            id);
    var replay = payments.confirm(id, command("original", 400));
    assertThat(replay.paidAmount()).isEqualByComparingTo("400");
    assertThat(replay.receivableAmount()).isNull();
    assertThat(replay.remainingAmount()).isNull();
    assertThat(replay.matchingConfirmed()).isFalse();
    assertThat(replay.paymentAllowed()).isFalse();
    assertThatThrownBy(() -> payments.confirm(id, command("original", 401)))
        .isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> payments.confirm(id, command("new-cash", 400)))
        .isInstanceOf(ConflictException.class);
    assertThat(
            jdbc.queryForList(
                "select * from partner_payment_events where target_type='AUCTION_PROCEEDS' and target_id=? order by id",
                id))
        .isEqualTo(before);
    assertThat(events.findById(received.getId()).orElseThrow().getExternalUid())
        .isEqualTo("MANUAL:AUCTION_SETTLEMENT:999901:original");
  }

  private ManualPaymentCommand command(String key, long amount) {
    return new ManualPaymentCommand(
        amount, LocalDate.of(2026, 10, 7), key, "현금", null, "수납자", null);
  }

  private Fixture seed(AuctionInspectionStatus inspection) {
    var house =
        partners.saveAndFlush(
            new BusinessPartner(
                "자료 " + UUID.randomUUID(), PartnerType.AUCTION_HOUSE, null, null, null, null));
    var date = LocalDate.of(2026, 10, 7);
    var shipment = new AuctionShipment(date, house.getId(), PartnerType.AUCTION_HOUSE);
    var lot = new AuctionShipmentLot("출하 품목", "품종", null, null, 1);
    var attempt = new AuctionAttempt(date, 1, AuctionAttemptStatus.SOLD, null, null);
    var line = new AuctionResultLine(date, null, 1, 1000, 1000, null, inspection);
    attempt.addResultLine(line);
    lot.addAttempt(attempt);
    shipment.addLot(lot);
    shipments.saveAndFlush(shipment);
    return new Fixture(house.getId(), line.getId(), lot.getId());
  }

  private record Fixture(Long houseId, Long resultId, Long lotId) {}
}
