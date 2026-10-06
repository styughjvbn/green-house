package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.greenhouse.backend.auction.application.AuctionTrackingService;
import com.greenhouse.backend.auction.application.RecordAuctionResultCommand;
import com.greenhouse.backend.auction.domain.AuctionAttemptStatus;
import com.greenhouse.backend.auction.domain.AuctionResultLineInput;
import com.greenhouse.backend.auction.domain.AuctionShipment;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import com.greenhouse.backend.auction.dto.AuctionLotResponse;
import com.greenhouse.backend.auction.dto.AuctionLotReturnRequest;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.application.SalesSlipCreationService;
import com.greenhouse.backend.sales.application.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.application.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.settlement.application.AuctionSettlementService;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@TestPropertySource(properties = "app.orchid-ledger.writer-version=1.1.0")
class AuctionCommandIdempotencyPostgresE2ETest extends WorkE2ETestBase {

  private static final LocalDate DATE = LocalDate.of(2043, 1, 1);
  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private BusinessPartnerRepository partners;
  @Autowired private SalesSlipCreationService creation;
  @Autowired private AuctionTrackingService auctions;
  @Autowired private OrchidGroupLedgerTestFixture ledgerFixture;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AuctionSettlementService settlements;
  @Autowired private AuctionShipmentRepository shipments;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EntityManager entityManager;
  private Long lotId;
  private Long auctionHouseId;

  @BeforeEach
  void seed() {
    jdbc.execute("TRUNCATE TABLE sales_slips, auction_shipments CONTINUE IDENTITY CASCADE");
    seeder.resetKeepingSequences();
    Long groupId = seeder.seedContractScenario().orchidGroupId();
    UUID key = UUID.randomUUID();
    ledgerFixture.seedBaseline(key, DATE, "1.0.0");
    ledgerFixture.activate(key);
    var partner =
        partners.saveAndFlush(
            new BusinessPartner(
                "경매 재전송 " + key, PartnerType.AUCTION_HOUSE, null, null, null, null));
    auctionHouseId = partner.getId();
    var slip =
        creation.create(
            new SalesSlipCommand(
                DATE,
                SalesType.AUCTION,
                partner.getId(),
                null,
                null,
                SalesSlip.STATUS_AUCTION_SHIPMENT_COMPLETED,
                null,
                null,
                List.of(
                    new SalesSlipItemInput(
                        "E2E 난",
                        "팔레놉시스",
                        "A",
                        40,
                        0,
                        null,
                        List.of(new SalesSlipAllocationInput(groupId, 40))))));
    lotId = slip.items().getFirst().auctionShipmentLotId();
  }

  @Test
  void retryOfAutomaticPartialResultDoesNotSellAgain() throws Exception {
    String body = resultBody("partial-result", "PARTIALLY_SOLD", 10);
    var first = post(resultsPath(), body);
    assertThat(first.status()).isEqualTo(200);
    var beforeRetry = snapshot();
    var retry = post(resultsPath(), body);
    assertThat(retry.status()).isEqualTo(200);
    assertThat(auctions.getLot(lotId).soldQuantity()).isEqualTo(10);
    assertThat(auctions.getLot(lotId).attempts()).hasSize(1);
    assertThat(retry.data()).isEqualTo(first.data());
    assertThat(snapshot()).isEqualTo(beforeRetry);
    assertThat(first.data().path("attempts").get(0).path("id").isIntegralNumber()).isTrue();
    assertThat(
            first
                .data()
                .path("attempts")
                .get(0)
                .path("resultLines")
                .get(0)
                .path("id")
                .isIntegralNumber())
        .isTrue();
    assertThat(first.data().path("statusHistory").get(0).path("id").isIntegralNumber()).isTrue();
  }

  @Test
  void retryOfPartialReturnDoesNotReturnAgain() throws Exception {
    assertThat(post(resultsPath(), resultBody("failed", "FAILED", 1)).status()).isEqualTo(200);
    String body = returnBody("partial-return", "10");
    var first = post(returnsPath(), body);
    assertThat(first.status()).isEqualTo(200);
    var beforeRetry = snapshot();
    var retry = post(returnsPath(), body);
    assertThat(retry.status()).isEqualTo(200);
    assertThat(auctions.getLot(lotId).returnedQuantity()).isEqualTo(10);
    assertThat(retry.data()).isEqualTo(first.data());
    assertThat(snapshot()).isEqualTo(beforeRetry);
  }

  @ParameterizedTest
  @EnumSource(AuctionAttemptStatus.class)
  void allResultStatusesReplayAfterStateChanges(AuctionAttemptStatus status) throws Exception {
    String body =
        resultBody("result", status.name(), status == AuctionAttemptStatus.SOLD ? 40 : 10);
    var first = post(resultsPath(), body);
    assertThat(first.status()).isEqualTo(200);
    var before = snapshot();
    var retry = post(resultsPath(), body);
    assertThat(retry.status()).isEqualTo(200);
    assertThat(retry.data()).isEqualTo(first.data());
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @CsvSource({
    "FAILED,10",
    "FAILED,40",
    "FAILED,null",
    "RETURN_INFERRED,10",
    "RETURN_INFERRED,null"
  })
  void partialAndFullReturnsReplayEvenWhenNoQuantityRemains(String status, String quantity)
      throws Exception {
    assertThat(post(resultsPath(), resultBody("setup", status, 1)).status()).isEqualTo(200);
    String body = returnBody("return", quantity);
    var first = post(returnsPath(), body);
    assertThat(first.status()).isEqualTo(200);
    var before = snapshot();
    var retry = post(returnsPath(), body);
    assertThat(retry.status()).isEqualTo(200);
    assertThat(retry.data()).isEqualTo(first.data());
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void oldPartialResultReplaysOriginalResponseAfterAnotherRealResultAndSettlement()
      throws Exception {
    String body = resultBody("first", "PARTIALLY_SOLD", 10);
    var first = post(resultsPath(), body);
    assertThat(first.status()).isEqualTo(200);
    assertThat(post(resultsPath(), resultBody("second", "PARTIALLY_SOLD", 10)).status())
        .isEqualTo(200);
    var settlement = settlements.rebuild(auctionHouseId, DATE);
    assertThat(settlement.grossAmount()).isEqualTo(20_000);
    assertThat(settlement.lines()).hasSize(2);
    var before = snapshot();
    var retry = post(resultsPath(), body);
    assertThat(retry.status()).isEqualTo(200);
    assertThat(retry.data()).isEqualTo(first.data());
    assertThat(auctions.getLot(lotId).soldQuantity()).isEqualTo(20);
    assertThat(auctions.getLot(lotId).attempts())
        .extracting(attempt -> attempt.attemptNo())
        .containsExactly(1, 2);
    assertThat(snapshot()).isEqualTo(before);
    assertThat(settlements.rebuild(auctionHouseId, DATE).grossAmount()).isEqualTo(20_000);
  }

  @Test
  void oldPartialReturnReplaysOriginalResponseAfterAnotherRealReturn() throws Exception {
    assertThat(post(resultsPath(), resultBody("setup", "FAILED", 1)).status()).isEqualTo(200);
    String body = returnBody("first", "10");
    var first = post(returnsPath(), body);
    assertThat(first.status()).isEqualTo(200);
    assertThat(post(returnsPath(), returnBody("second", "10")).status()).isEqualTo(200);
    var before = snapshot();
    var retry = post(returnsPath(), body);
    assertThat(retry.status()).isEqualTo(200);
    assertThat(retry.data()).isEqualTo(first.data());
    assertThat(auctions.getLot(lotId).returnedQuantity()).isEqualTo(20);
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "auctionDate",
        "attemptNo",
        "attemptStatus",
        "failedReason",
        "memo",
        "quantity",
        "unitPrice",
        "auctionGrade",
        "note",
        "inspectionStatus"
      })
  void sameResultKeyWithChangedContentConflictsWithoutSideEffects(String field) throws Exception {
    String body = resultBody("conflict", "PARTIALLY_SOLD", 10);
    assertThat(post(resultsPath(), body).status()).isEqualTo(200);
    ObjectNode changed = (ObjectNode) objectMapper.readTree(body);
    switch (field) {
      case "auctionDate" -> changed.put(field, "2043-01-02");
      case "attemptNo" -> changed.put(field, 2);
      case "attemptStatus" -> changed.put(field, "SOLD");
      case "quantity" -> ((ObjectNode) changed.path("resultLines").get(0)).put(field, 11);
      case "unitPrice" -> ((ObjectNode) changed.path("resultLines").get(0)).put(field, 2000);
      case "inspectionStatus" ->
          ((ObjectNode) changed.path("resultLines").get(0)).put(field, "RETURN_INFERRED");
      case "auctionGrade", "note" ->
          ((ObjectNode) changed.path("resultLines").get(0)).put(field, "다른 값");
      default -> changed.put(field, "다른 값");
    }
    var before = snapshot();
    var conflict = post(resultsPath(), changed.toString());
    assertThat(conflict.status()).isEqualTo(409);
    assertThat(conflict.body().path("error").path("code").asText())
        .isEqualTo("AUCTION_REQUEST_KEY_CONFLICT");
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"returnedQuantity", "returnDate", "worker", "memo"})
  void sameReturnKeyWithChangedContentConflictsBeforeCurrentStateValidation(String field)
      throws Exception {
    assertThat(post(resultsPath(), resultBody("setup", "FAILED", 1)).status()).isEqualTo(200);
    String body = returnBody("conflict", "null");
    assertThat(post(returnsPath(), body).status()).isEqualTo(200);
    ObjectNode changed = (ObjectNode) objectMapper.readTree(body);
    if (field.equals("returnedQuantity")) changed.put(field, 40);
    else changed.put(field, field.equals("returnDate") ? "2043-01-03" : "다른 값");
    var before = snapshot();
    var conflict = post(returnsPath(), changed.toString());
    assertThat(conflict.status()).isEqualTo(409);
    assertThat(conflict.body().path("error").path("code").asText())
        .isEqualTo("AUCTION_REQUEST_KEY_CONFLICT");
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"RESULT", "RETURN"})
  void missingBlankAndOversizedKeysAreRejectedBeforeAnyEffects(String type) throws Exception {
    var before = snapshot();
    for (String key : List.of("", " ", "k".repeat(101))) {
      String body =
          type.equals("RESULT") ? resultBody(key, "PARTIALLY_SOLD", 10) : returnBody(key, "10");
      assertThat(post(type.equals("RESULT") ? resultsPath() : returnsPath(), body).status())
          .isEqualTo(400);
    }
    ObjectNode missing =
        (ObjectNode)
            objectMapper.readTree(
                type.equals("RESULT")
                    ? resultBody("remove", "PARTIALLY_SOLD", 10)
                    : returnBody("remove", "10"));
    missing.remove("idempotencyKey");
    assertThat(
            post(type.equals("RESULT") ? resultsPath() : returnsPath(), missing.toString())
                .status())
        .isEqualTo(400);
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", " B "})
  void persistsReportedGradeWithoutShipmentFallbackAndReplays(String grade) throws Exception {
    ObjectNode body =
        (ObjectNode) objectMapper.readTree(resultBody("reported-grade", "PARTIALLY_SOLD", 10));
    ObjectNode inputLine = (ObjectNode) body.path("resultLines").get(0);
    if (grade == null) inputLine.remove("auctionGrade");
    else inputLine.put("auctionGrade", grade);
    var first = post(resultsPath(), body.toString());
    assertThat(first.status()).isEqualTo(200);
    assertThat(first.data().path("shipmentGrade").asText()).isEqualTo("A");
    var rows = first.data().path("attempts").get(0).path("resultLines");
    String expected = grade == null || grade.isBlank() ? null : "B";
    assertThat(rows.get(0).path("auctionGrade").asText(null)).isEqualTo(expected);
    assertThat(rows.get(1).path("auctionGrade").asText(null)).isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT r.auction_grade FROM auction_result_lines r JOIN auction_attempts a ON a.id = r.auction_attempt_id WHERE a.shipment_lot_id = ? AND r.quantity = 10",
                String.class,
                lotId))
        .isEqualTo(expected);
    var before = snapshot();
    assertThat(post(resultsPath(), body.toString()).data()).isEqualTo(first.data());
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void invalidResultDoesNotConsumeKeyAndJsonObjectOrderIsIgnored() throws Exception {
    var before = snapshot();
    assertThat(post(resultsPath(), resultBody("retry", "PARTIALLY_SOLD", 50)).status())
        .isEqualTo(400);
    assertThat(snapshot()).isEqualTo(before);
    var first = post(resultsPath(), resultBody("retry", "PARTIALLY_SOLD", 10));
    assertThat(first.status()).isEqualTo(200);
    var reordered =
        post(
            resultsPath(),
            """
        {"attemptStatus":"PARTIALLY_SOLD","resultLines":[{"unitPrice":1000,"quantity":10,"auctionGrade":"A"}],
         "auctionDate":"2043-01-01","idempotencyKey":"retry"}
        """);
    assertThat(reordered.status()).isEqualTo(200);
    assertThat(reordered.data()).isEqualTo(first.data());
  }

  @Test
  void requestKeysAreScopedToTheLotAndCommandType() throws Exception {
    String failed = resultBody("shared", "FAILED", 1);
    assertThat(post(resultsPath(), failed).status()).isEqualTo(200);
    assertThat(post(returnsPath(), returnBody("shared", "10")).status()).isEqualTo(200);
    var shipment = new AuctionShipment(DATE, auctionHouseId, PartnerType.AUCTION_HOUSE);
    shipment.addLot(new AuctionShipmentLot("별도 lot", "품종", "A", null, 40));
    var otherLotId = shipments.saveAndFlush(shipment).getLots().getFirst().getId();
    assertThat(post("/api/auction-lots/" + otherLotId + "/results", failed).status())
        .isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auction_command_receipts WHERE request_key = 'shared'",
                Integer.class))
        .isEqualTo(3);
    var before = snapshot();
    assertThat(post(resultsPath(), failed).status()).isEqualTo(200);
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void explicitAttemptNumberReplaysAndStillPreventsANewDuplicateAttempt() throws Exception {
    ObjectNode body =
        (ObjectNode) objectMapper.readTree(resultBody("explicit", "PARTIALLY_SOLD", 10));
    body.put("attemptNo", 7);
    var first = post(resultsPath(), body.toString());
    assertThat(first.status()).isEqualTo(200);
    var before = snapshot();
    assertThat(post(resultsPath(), body.toString()).data()).isEqualTo(first.data());
    body.put("idempotencyKey", "genuinely-new");
    assertThat(post(resultsPath(), body.toString()).status()).isEqualTo(400);
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void changedLineOrderIsAnExplicitConflict() throws Exception {
    String body =
        """
        {"idempotencyKey":"ordered","auctionDate":"2043-01-01","attemptStatus":"PARTIALLY_SOLD",
         "resultLines":[{"quantity":3,"unitPrice":1000},{"quantity":7,"unitPrice":2000}]}
        """;
    assertThat(post(resultsPath(), body).status()).isEqualTo(200);
    var changed = (ObjectNode) objectMapper.readTree(body);
    var lines = changed.withArray("resultLines");
    var originalFirst = lines.get(0);
    lines.set(0, lines.get(1));
    lines.set(1, originalFirst);
    var before = snapshot();
    var conflict = post(resultsPath(), changed.toString());
    assertThat(conflict.status()).isEqualTo(409);
    assertThat(conflict.body().path("error").path("code").asText())
        .isEqualTo("AUCTION_REQUEST_KEY_CONFLICT");
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"RESULT", "RETURN"})
  void receiptSaveFailureRollsBackAllFactsAndTheSameKeyCanBeRetried(String type) throws Exception {
    if (type.equals("RETURN"))
      assertThat(post(resultsPath(), resultBody("setup", "FAILED", 1)).status()).isEqualTo(200);
    var before = snapshot();
    jdbc.execute(
        "ALTER TABLE auction_command_receipts ADD CONSTRAINT test_reject_receipt CHECK (request_key <> 'rollback')");
    try {
      assertThatThrownBy(() -> command(type, "rollback", 10))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(snapshot()).isEqualTo(before);
    } finally {
      jdbc.execute("ALTER TABLE auction_command_receipts DROP CONSTRAINT test_reject_receipt");
    }
    var first = command(type, "rollback", 10);
    var committed = snapshot();
    assertThat(command(type, "rollback", 10)).isEqualTo(first);
    assertThat(snapshot()).isEqualTo(committed);
  }

  @ParameterizedTest
  @ValueSource(strings = {"RESULT", "RETURN"})
  void concurrentRetriesWaitForTheRootLockAndReplayTheWinner(String type) throws Exception {
    if (type.equals("RETURN"))
      assertThat(post(resultsPath(), resultBody("setup", "FAILED", 1)).status()).isEqualTo(200);
    var first = new AtomicReference<AuctionLotResponse>();
    var retry = new AtomicReference<AuctionLotResponse>();
    assertThat(
            compete(
                () -> first.set(command(type, "race", 10)),
                () -> retry.set(command(type, "race", 10)),
                false))
        .isNull();
    assertThat(retry.get()).isEqualTo(first.get());
    assertThat(
            type.equals("RESULT")
                ? auctions.getLot(lotId).soldQuantity()
                : auctions.getLot(lotId).returnedQuantity())
        .isEqualTo(10);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auction_command_receipts WHERE request_key = 'race'",
                Integer.class))
        .isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"RESULT", "RETURN"})
  void concurrentChangedPayloadConflictsAfterTheWinnerCommits(String type) throws Exception {
    if (type.equals("RETURN"))
      assertThat(post(resultsPath(), resultBody("setup", "FAILED", 1)).status()).isEqualTo(200);
    var afterWinner = new AtomicReference<Map<String, List<String>>>();
    var failure =
        compete(
            () -> {
              command(type, "race", 10);
              entityManager.flush();
              afterWinner.set(snapshot());
            },
            () -> command(type, "race", 11),
            false);
    assertThat(failure).isInstanceOf(ConflictException.class);
    assertThat(((ConflictException) failure).getCode()).isEqualTo("AUCTION_REQUEST_KEY_CONFLICT");
    assertThat(snapshot()).isEqualTo(afterWinner.get());
  }

  @Test
  void aWaitingRetryAppliesOnceWhenTheFirstTransactionRollsBack() throws Exception {
    var applied = new AtomicReference<AuctionLotResponse>();
    assertThat(
            compete(
                () -> command("RESULT", "rollback-race", 10),
                () -> applied.set(command("RESULT", "rollback-race", 10)),
                true))
        .isNull();
    assertThat(applied.get().soldQuantity()).isEqualTo(10);
    assertThat(auctions.getLot(lotId).attempts()).hasSize(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM auction_command_receipts", Integer.class))
        .isEqualTo(1);
  }

  private AuctionLotResponse command(String type, String key, int quantity) {
    return type.equals("RESULT")
        ? auctions.addResult(
            lotId,
            new RecordAuctionResultCommand(
                key,
                DATE,
                null,
                AuctionAttemptStatus.PARTIALLY_SOLD,
                null,
                null,
                List.of(new AuctionResultLineInput("A", quantity, 1000, null, null))))
        : auctions.confirmReturn(
            lotId, new AuctionLotReturnRequest(key, quantity, DATE.plusDays(1), null, null));
  }

  private Map<String, List<String>> snapshot() {
    var rows = new LinkedHashMap<String, List<String>>();
    for (String table :
        List.of(
            "sales_slips",
            "sales_slip_items",
            "sales_slip_item_allocations",
            "auction_shipments",
            "auction_shipment_lots",
            "auction_attempts",
            "auction_result_lines",
            "auction_lot_status_history",
            "auction_command_receipts",
            "auction_settlements",
            "auction_settlement_lines",
            "orchid_groups",
            "orchid_group_mutations",
            "orchid_group_mutation_entries",
            "sales_inventory_movements",
            "audit_events",
            "partner_balance_summaries")) {
      rows.put(
          table,
          jdbc.queryForList(
              "SELECT to_jsonb(row)::text FROM " + table + " row ORDER BY 1", String.class));
    }
    return rows;
  }

  private Throwable compete(Runnable winner, Runnable loser, boolean rollbackWinner)
      throws Exception {
    var winnerReady = new CountDownLatch(1);
    var loserReady = new CountDownLatch(1);
    var finishWinner = new CountDownLatch(1);
    var winnerPid = new AtomicInteger();
    var loserPid = new AtomicInteger();
    var transaction = new TransactionTemplate(transactionManager);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () ->
                  transaction.executeWithoutResult(
                      status -> {
                        winnerPid.set(
                            jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        winner.run();
                        entityManager.flush();
                        winnerReady.countDown();
                        await(finishWinner);
                        if (rollbackWinner) status.setRollbackOnly();
                      }));
      try {
        assertThat(winnerReady.await(10, TimeUnit.SECONDS)).isTrue();
        var second =
            executor.submit(
                () -> {
                  try {
                    transaction.executeWithoutResult(
                        status -> {
                          loserPid.set(
                              jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                          loserReady.countDown();
                          loser.run();
                        });
                    return null;
                  } catch (RuntimeException exception) {
                    return exception;
                  }
                });
        assertThat(loserReady.await(10, TimeUnit.SECONDS)).isTrue();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean blocked = false;
        while (!second.isDone() && System.nanoTime() < deadline) {
          blocked =
              Boolean.TRUE.equals(
                  jdbc.queryForObject(
                      "SELECT ? = ANY(pg_blocking_pids(?))",
                      Boolean.class,
                      winnerPid.get(),
                      loserPid.get()));
          if (blocked) break;
          Thread.sleep(25);
        }
        assertThat(blocked).as("retry waits for the winning PostgreSQL row lock").isTrue();
        finishWinner.countDown();
        first.get(20, TimeUnit.SECONDS);
        return second.get(20, TimeUnit.SECONDS);
      } finally {
        finishWinner.countDown();
      }
    }
  }

  private void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  private String resultsPath() {
    return "/api/auction-lots/" + lotId + "/results";
  }

  private String returnsPath() {
    return "/api/auction-lots/" + lotId + "/confirm-return";
  }

  private String resultBody(String key, String status, int quantity) {
    if (status.equals("FAILED") || status.equals("RETURN_INFERRED")) {
      return """
          {"idempotencyKey":"%s","auctionDate":"2043-01-01","attemptStatus":"%s"}
          """
          .formatted(key, status);
    }
    return """
        {"idempotencyKey":"%s","auctionDate":"2043-01-01","attemptStatus":"%s",
         "resultLines":[{"auctionGrade":"A","quantity":%d,"unitPrice":1000}]}
        """
        .formatted(key, status, quantity);
  }

  private String returnBody(String key, String quantity) {
    return """
        {"idempotencyKey":"%s","returnedQuantity":%s,"returnDate":"2043-01-02"}
        """
        .formatted(key, quantity);
  }
}
