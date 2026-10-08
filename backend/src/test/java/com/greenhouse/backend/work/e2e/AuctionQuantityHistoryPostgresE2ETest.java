package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.sales.application.auction.AuctionProceedsPaymentService;
import com.greenhouse.backend.sales.application.auction.AuctionProceedsReader;
import com.greenhouse.backend.sales.application.auction.AuctionProceedsService;
import com.greenhouse.backend.sales.application.auction.AuctionTrackingService;
import com.greenhouse.backend.sales.application.auction.RecordAuctionResultCommand;
import com.greenhouse.backend.sales.application.payment.ManualPaymentCommand;
import com.greenhouse.backend.sales.application.payment.PaymentService;
import com.greenhouse.backend.sales.domain.auction.AuctionAttemptStatus;
import com.greenhouse.backend.sales.domain.auction.AuctionLotStatus;
import com.greenhouse.backend.sales.domain.auction.AuctionResultLineInput;
import com.greenhouse.backend.sales.domain.auction.AuctionShipment;
import com.greenhouse.backend.sales.domain.auction.AuctionShipmentLot;
import com.greenhouse.backend.sales.domain.partner.BusinessPartner;
import com.greenhouse.backend.sales.domain.partner.PartnerType;
import com.greenhouse.backend.sales.dto.auction.AuctionLotAdjustmentRequest;
import com.greenhouse.backend.sales.dto.auction.AuctionLotReturnRequest;
import com.greenhouse.backend.sales.dto.auction.AuctionLotStatusRequest;
import com.greenhouse.backend.sales.dto.auction.AuctionStatusHistoryResponse;
import com.greenhouse.backend.sales.repository.auction.AuctionShipmentRepository;
import com.greenhouse.backend.sales.repository.partner.BusinessPartnerRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
class AuctionQuantityHistoryPostgresE2ETest extends WorkE2ETestBase {

  @org.springframework.beans.factory.annotation.Autowired
  private AuctionProceedsPaymentService auctionPayments;

  private static final LocalDate DATE = LocalDate.of(2026, 10, 4);
  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private AuctionShipmentRepository shipments;
  @Autowired private BusinessPartnerRepository partners;
  @Autowired private AuctionTrackingService auctions;
  @Autowired private AuctionProceedsReader settlements;
  @Autowired private AuctionProceedsService proceedsService;
  @Autowired private PaymentService payments;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;
  private Long lotId;
  private Long partnerId;

  @BeforeEach
  void seed() {
    jdbc.execute("TRUNCATE auction_shipments CONTINUE IDENTITY CASCADE");
    seeder.resetKeepingSequences();
    var partner =
        partners.saveAndFlush(
            new BusinessPartner(
                "수량 이력 " + UUID.randomUUID(), PartnerType.AUCTION_HOUSE, null, null, null, null));
    partnerId = partner.getId();
    var shipment = new AuctionShipment(DATE, partnerId, PartnerType.AUCTION_HOUSE);
    var lot = new AuctionShipmentLot("난", "품종", "A", null, 40);
    shipment.addLot(lot);
    shipments.saveAndFlush(shipment);
    lotId = lot.getId();
  }

  @Test
  void everyPartialReturnHasQuantitiesActorReasonAndCommittedHistoryId() throws Exception {
    auctions.addResult(lotId, result("failed", AuctionAttemptStatus.FAILED));
    auctions.confirmReturn(lotId, returned("first"));
    var second = auctions.confirmReturn(lotId, returned("second"));
    assertThat(second.statusHistory()).hasSize(3);
    var history = second.statusHistory().getLast();
    assertThat(history.previousStatus()).isEqualTo(AuctionLotStatus.PARTIALLY_RETURNED);
    assertThat(history.newStatus()).isEqualTo(AuctionLotStatus.PARTIALLY_RETURNED);
    assertQuantities(history, 0, 30, 10, 0, 20, 20);
    assertThat(history.worker()).isEqualTo("작업자");
    assertThat(history.memo()).isEqualTo("second 반환");
    assertThat(history.reason()).isEqualTo("부분반환 확인");
    assertThat(history.id()).isPositive();
    var detail = get("/api/auction-lots/" + lotId + "/timeline");
    assertThat(detail.status()).isEqualTo(200);
    assertThat(detail.data().path("statusHistory").get(2).path("previousReturnedQuantity").asInt())
        .isEqualTo(10);
    assertThat(detail.data().path("quantityAdjustmentAllowed").asBoolean()).isFalse();
    var before = snapshot();
    var replay = auctions.confirmReturn(lotId, returned("second"));
    assertThat(replay).isEqualTo(second);
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void sameStatusAdjustmentsPersistSnapshotsAndNoOpCreatesNoFact() {
    auctions.adjust(lotId, adjustment(0, 30, 10));
    var second = auctions.adjust(lotId, adjustment(0, 20, 20));
    assertThat(second.statusHistory()).hasSize(2);
    assertQuantities(second.statusHistory().getLast(), 0, 30, 10, 0, 20, 20);
    assertThat(second.statusHistory().getLast().id()).isPositive();
    assertThat(second.quantityAdjustmentAllowed()).isTrue();
    var before = snapshot();
    auctions.adjust(lotId, adjustment(0, 20, 20));
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void repeatedPartialResultsCaptureTheirDifferentQuantityBoundaries() {
    auctions.addResult(lotId, result("first", AuctionAttemptStatus.PARTIALLY_SOLD));
    var second = auctions.addResult(lotId, result("second", AuctionAttemptStatus.PARTIALLY_SOLD));
    assertThat(second.statusHistory()).hasSize(2);
    assertQuantities(second.statusHistory().getLast(), 10, 30, 0, 20, 20, 0);
    assertThat(second.statusHistory().getLast().previousStatus())
        .isEqualTo(AuctionLotStatus.PARTIALLY_SOLD);
    assertThat(second.attempts()).hasSize(2);
  }

  @Test
  void failedSameStatusAdjustmentDoesNotPersistEitherTheCorrectionOrItsHistory() {
    auctions.adjust(lotId, adjustment(0, 30, 10));
    var before = snapshot();
    jdbc.execute(
        "ALTER TABLE auction_lot_status_history ADD CONSTRAINT test_block_adjustment CHECK (new_waiting_quantity <> 20)");
    try {
      assertThatThrownBy(() -> auctions.adjust(lotId, adjustment(0, 20, 20)))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(snapshot()).isEqualTo(before);
    } finally {
      jdbc.execute("ALTER TABLE auction_lot_status_history DROP CONSTRAINT test_block_adjustment");
    }
    assertThat(auctions.adjust(lotId, adjustment(0, 20, 20)).statusHistory()).hasSize(2);
  }

  @ParameterizedTest
  @EnumSource(AuctionAttemptStatus.class)
  void allRecordedAttemptKindsBlockDirectChangesEvenAfterManualStatusCorrection(
      AuctionAttemptStatus status) throws Exception {
    auctions.addResult(lotId, result("result", status));
    auctions.changeStatus(
        lotId,
        new AuctionLotStatusRequest(AuctionLotStatus.REAUCTION_WAITING, "상태만 정정", "작업자", null));
    var before = snapshot();
    var response =
        post(
            "/api/auction-lots/" + lotId + "/adjust-quantity",
            objectMapper.writeValueAsString(adjustment(0, 35, 5)));
    assertThat(response.status()).isEqualTo(409);
    assertThat(response.body().path("error").path("code").asText())
        .isEqualTo("AUCTION_QUANTITY_ADJUSTMENT_LOCKED");
    assertThat(auctions.getLot(lotId).quantityAdjustmentAllowed()).isFalse();
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void confirmedReturnWithoutAnAttemptCannotBeDeletedByQuantityCorrection() {
    auctions.changeStatus(
        lotId,
        new AuctionLotStatusRequest(AuctionLotStatus.REAUCTION_WAITING, "운영 확인", "작업자", null));
    auctions.confirmReturn(lotId, returned("return"));
    var before = snapshot();
    assertThatThrownBy(() -> auctions.adjust(lotId, adjustment(0, 40, 0)))
        .isInstanceOf(ConflictException.class);
    assertThat(snapshot()).isEqualTo(before);
    assertThat(auctions.getLot(lotId).returnedQuantity()).isEqualTo(10);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void settlementAndPaymentSnapshotsSurviveRejectedQuantityCorrection(boolean paid) {
    auctions.addResult(lotId, result("sold", AuctionAttemptStatus.SOLD));
    var resultIds =
        jdbc.queryForList(
            "select line.id from auction_result_lines line join auction_attempts attempt on attempt.id=line.auction_attempt_id where attempt.shipment_lot_id=?",
            Long.class,
            lotId);
    Long id = proceedsService.record(partnerId, "제공 지급 자료", 40000L, 40000L, resultIds);
    proceedsService.confirm(id, "확인자");
    var settlement = settlements.get(id);
    if (paid)
      auctionPayments.confirm(
          settlement.id(),
          new ManualPaymentCommand(40_000L, DATE, "paid", "BANK", "입금자", "작업자", null));
    var before = snapshot();
    assertThatThrownBy(() -> auctions.adjust(lotId, adjustment(30, 10, 0)))
        .isInstanceOf(ConflictException.class);
    assertThat(snapshot()).isEqualTo(before);
    var rebuilt = settlements.get(settlement.id());
    assertThat(rebuilt.reportedGrossAmount()).isEqualTo(40_000);
    assertThat(rebuilt.resultIds()).isEqualTo(settlement.resultIds());
    assertThat(rebuilt.paidAmount()).isEqualByComparingTo(paid ? "40000" : "0");
  }

  @Test
  void historyInsertFailureRollsBackReturnAndReceiptThenTheSameKeyCanSucceed() {
    auctions.addResult(lotId, result("failed", AuctionAttemptStatus.FAILED));
    auctions.confirmReturn(lotId, returned("first"));
    var before = snapshot();
    jdbc.execute(
        "ALTER TABLE auction_lot_status_history ADD CONSTRAINT test_block_return CHECK (memo IS DISTINCT FROM 'second 반환')");
    try {
      assertThatThrownBy(() -> auctions.confirmReturn(lotId, returned("second")))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(snapshot()).isEqualTo(before);
    } finally {
      jdbc.execute("ALTER TABLE auction_lot_status_history DROP CONSTRAINT test_block_return");
    }
    assertThat(auctions.confirmReturn(lotId, returned("second")).returnedQuantity()).isEqualTo(20);
  }

  @Test
  void concurrentPartialReturnsSerializeAndCaptureCommittedPreviousQuantities() throws Exception {
    auctions.addResult(lotId, result("failed", AuctionAttemptStatus.FAILED));
    var race =
        compete(
            () -> auctions.confirmReturn(lotId, returned("first")),
            () -> auctions.confirmReturn(lotId, returned("second")));
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    var lot = auctions.getLot(lotId);
    assertThat(lot.returnedQuantity()).isEqualTo(20);
    assertThat(lot.statusHistory()).hasSize(3);
    assertQuantities(lot.statusHistory().getLast(), 0, 30, 10, 0, 20, 20);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void resultAndAdjustmentRecheckFactsAfterWaitingForTheLotRoot(boolean resultWins)
      throws Exception {
    Runnable result =
        () -> auctions.addResult(lotId, result("partial", AuctionAttemptStatus.PARTIALLY_SOLD));
    Runnable correction = () -> auctions.adjust(lotId, adjustment(0, 35, 5));
    var race = compete(resultWins ? result : correction, resultWins ? correction : result);
    assertThat(race.firstFailure()).isNull();
    if (resultWins) {
      assertThat(race.secondFailure()).isInstanceOf(ConflictException.class);
      assertThat(auctions.getLot(lotId).returnedQuantity()).isZero();
      assertThat(auctions.getLot(lotId).statusHistory()).hasSize(1);
    } else {
      assertThat(race.secondFailure()).isNull();
      var lot = auctions.getLot(lotId);
      assertThat(lot.returnedQuantity()).isEqualTo(5);
      assertThat(lot.statusHistory()).hasSize(2);
      assertQuantities(lot.statusHistory().getLast(), 0, 35, 5, 10, 25, 5);
    }
    assertThat(auctions.getLot(lotId).soldQuantity()).isEqualTo(10);
  }

  @Test
  void legacyReceiptWithoutNewFieldsReplaysWithoutInventingSnapshots() {
    auctions.addResult(lotId, result("old", AuctionAttemptStatus.FAILED));
    jdbc.execute(
        "UPDATE auction_command_receipts SET response_snapshot = jsonb_set(response_snapshot - 'quantityAdjustmentAllowed', '{statusHistory}', (SELECT jsonb_agg(h - 'previousSoldQuantity' - 'newSoldQuantity' - 'previousWaitingQuantity' - 'newWaitingQuantity' - 'previousReturnedQuantity' - 'newReturnedQuantity') FROM jsonb_array_elements(response_snapshot->'statusHistory') h))");
    var before = snapshot();
    var replay = auctions.addResult(lotId, result("old", AuctionAttemptStatus.FAILED));
    assertThat(replay.quantityAdjustmentAllowed()).isNull();
    assertThat(replay.statusHistory().getFirst().previousSoldQuantity()).isNull();
    assertThat(snapshot()).isEqualTo(before);
    assertThat(auctions.getLot(lotId).statusHistory().getFirst().previousSoldQuantity()).isZero();
  }

  private AuctionLotAdjustmentRequest adjustment(int sold, int waiting, int returned) {
    return new AuctionLotAdjustmentRequest(sold, waiting, returned, " 작업자 ", " 수량 확인 ");
  }

  private AuctionLotReturnRequest returned(String key) {
    return new AuctionLotReturnRequest(key, 10, DATE, " 작업자 ", " " + key + " 반환 ");
  }

  private RecordAuctionResultCommand result(String key, AuctionAttemptStatus status) {
    return new RecordAuctionResultCommand(
        key,
        DATE,
        null,
        status,
        null,
        "결과",
        List.of(
            new AuctionResultLineInput(
                "A", status == AuctionAttemptStatus.SOLD ? 40 : 10, 1000, null, null)));
  }

  private void assertQuantities(
      AuctionStatusHistoryResponse h, int ps, int pw, int pr, int ns, int nw, int nr) {
    assertThat(
            List.of(
                h.previousSoldQuantity(),
                h.previousWaitingQuantity(),
                h.previousReturnedQuantity(),
                h.newSoldQuantity(),
                h.newWaitingQuantity(),
                h.newReturnedQuantity()))
        .containsExactly(ps, pw, pr, ns, nw, nr);
  }

  private Race compete(Runnable first, Runnable second) throws Exception {
    var paused = new CountDownLatch(1);
    var resume = new CountDownLatch(1);
    var started = new CountDownLatch(1);
    var firstPid = new AtomicInteger();
    var secondPid = new AtomicInteger();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var winner =
          pool.submit(
              () ->
                  transaction(
                      firstPid,
                      null,
                      () -> {
                        first.run();
                        entityManager.flush();
                        paused.countDown();
                        try {
                          assertThat(resume.await(10, TimeUnit.SECONDS)).isTrue();
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                          throw new IllegalStateException(e);
                        }
                      }));
      try {
        assertThat(paused.await(10, TimeUnit.SECONDS)).isTrue();
        var loser = pool.submit(() -> transaction(secondPid, started, second));
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        boolean blocked = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!loser.isDone() && System.nanoTime() < deadline) {
          blocked =
              Boolean.TRUE.equals(
                  jdbc.queryForObject(
                      "SELECT ? = ANY(pg_blocking_pids(?))",
                      Boolean.class,
                      firstPid.get(),
                      secondPid.get()));
          if (blocked) break;
          Thread.sleep(25);
        }
        assertThat(blocked).isTrue();
        resume.countDown();
        return new Race(winner.get(20, TimeUnit.SECONDS), loser.get(20, TimeUnit.SECONDS));
      } finally {
        resume.countDown();
      }
    }
  }

  private Throwable transaction(AtomicInteger pid, CountDownLatch started, Runnable action) {
    try {
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                pid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                jdbc.execute("SET LOCAL lock_timeout = '15s'");
                if (started != null) started.countDown();
                action.run();
              });
      return null;
    } catch (RuntimeException e) {
      return e;
    }
  }

  private Map<String, List<String>> snapshot() {
    var state = new LinkedHashMap<String, List<String>>();
    for (String table :
        List.of(
            "auction_shipment_lots",
            "auction_attempts",
            "auction_result_lines",
            "auction_lot_status_history",
            "auction_command_receipts",
            "auction_proceeds",
            "auction_proceeds_results",
            "partner_payment_events",
            "partner_balance_summaries",
            "audit_events"))
      state.put(
          table,
          jdbc.queryForList(
              "SELECT to_jsonb(r)::text FROM " + table + " r ORDER BY 1", String.class));
    return state;
  }

  private record Race(Throwable firstFailure, Throwable secondFailure) {}
}
