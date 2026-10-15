package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.sales.api.document.SalesSlipDocument;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.document.application.SalesSlipCreationService;
import com.greenhouse.backend.sales.document.application.SalesSlipStatusService;
import com.greenhouse.backend.sales.document.application.command.SalesInitialReceiptInput;
import com.greenhouse.backend.sales.document.application.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.document.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.document.application.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.document.web.dto.SalesSlipStatusUpdateRequest;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import jakarta.persistence.EntityManagerFactory;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@TestPropertySource(properties = "app.orchid-ledger.writer-version=1.1.0")
class SalesCreationIdempotencyPostgresE2ETest extends WorkE2ETestBase {
  private static final LocalDate DATE = LocalDate.of(2044, 1, 1);
  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private BusinessPartnerRepository partners;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private OrchidGroupLedgerTestFixture ledgerFixture;
  @Autowired private SalesSlipCreationService creation;
  @Autowired private SalesSlipStatusService statuses;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EntityManagerFactory entityManagerFactory;
  private Long groupId;
  private Long partnerId;

  @BeforeEach
  void seed() {
    seeder.resetKeepingSequences();
    jdbc.execute(
        "TRUNCATE sales_creation_receipts, sales_slips, auction_shipments CONTINUE IDENTITY CASCADE");
    groupId = seeder.seedContractScenario().orchidGroupId();
    partnerId =
        partners
            .saveAndFlush(
                new BusinessPartner(
                    "판매 생성 " + UUID.randomUUID(), PartnerType.WHOLESALE, null, null, null, null))
            .getId();
    var key = UUID.randomUUID();
    ledgerFixture.seedBaseline(key, DATE, "1.0.0");
    ledgerFixture.activate(key);
  }

  @ParameterizedTest
  @CsvSource({
    "CASH,작성중,2000,부분입금",
    "CASH,출고 완료,5000,입금 완료",
    "BANK_TRANSFER,작성중,5000,입금 완료",
    "CASH,작성중,6000,입금 완료",
    "BANK_TRANSFER,출고 완료,7000,입금 완료"
  })
  void createsReceiptAndAllocationAtomicallyAndReplaysWithoutCollectingTwice(
      SalesInitialReceiptInput.Method method, String dispatch, long amount, String paymentStatus)
      throws Exception {
    var command = withReceipt(request(SalesType.DIRECT, dispatch, 5, null), amount, method);
    String body = objectMapper.writeValueAsString(command);
    var first = post("/api/sales-slips", body, Map.of("Idempotency-Key", "with-cash"));
    assertThat(first.status()).isEqualTo(201);
    assertThat(first.data().path("paidAmount").asLong()).isEqualTo(Math.min(amount, 5000));
    assertThat(first.data().path("remainingAmount").asLong()).isEqualTo(Math.max(0, 5000 - amount));
    assertThat(first.data().path("paymentStatus").asText()).isEqualTo(paymentStatus);
    assertThat(first.data().path("salesStatus").asText()).isEqualTo(dispatch);
    assertThat(post("/api/sales-slips", body, Map.of("Idempotency-Key", "with-cash")).data())
        .isEqualTo(first.data());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from partner_payment_events where event_type='PAYMENT_RECEIVED' and partner_id=?",
                Integer.class,
                partnerId))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select payment_method from partner_payment_events where event_type='PAYMENT_RECEIVED' and partner_id=?",
                String.class,
                partnerId))
        .isEqualTo(method == SalesInitialReceiptInput.Method.CASH ? "현금" : "계좌이체");
    assertThat(
            jdbc.queryForObject(
                "select unapplied_payment_amount from partner_balance_summaries where partner_id=?",
                Long.class,
                partnerId))
        .isEqualTo(Math.max(0, amount - 5000));
    assertThat(
            jdbc.queryForObject(
                "select amount from partner_payment_events where event_type='PAYMENT_RECEIVED' and partner_id=?",
                Long.class,
                partnerId))
        .isEqualTo(amount);
    var history =
        get("/api/payment-targets/SALES_SLIP/" + first.data().path("id").asLong() + "/history");
    assertThat(history.data().path("content").get(0).path("amount").asLong())
        .isEqualTo(Math.min(amount, 5000));
    var changed = withReceipt(command, amount == 5000 ? 4000 : 3000, method);
    assertThat(
            post(
                    "/api/sales-slips",
                    objectMapper.writeValueAsString(changed),
                    Map.of("Idempotency-Key", "with-cash"))
                .status())
        .isEqualTo(409);
    if (amount > 5000) {
      long receiptId =
          jdbc.queryForObject(
              "select id from partner_payment_events where event_type='PAYMENT_RECEIVED' and partner_id=?",
              Long.class,
              partnerId);
      var cash = get("/api/business-partners/" + partnerId + "/payment-receipts/" + receiptId);
      assertThat(cash.data().path("availableAmount").asLong()).isEqualTo(amount - 5000);
      assertThat(cash.data().path("reviewRequired").asBoolean()).isFalse();
      var next =
          post(
              "/api/sales-slips",
              objectMapper.writeValueAsString(request(2, null)),
              Map.of("Idempotency-Key", "next-document"));
      assertThat(next.status()).isEqualTo(201);
      var allocation =
          post(
              "/api/business-partners/" + partnerId + "/payment-allocations",
              objectMapper.writeValueAsString(
                  Map.of(
                      "allocationDate",
                      DATE.toString(),
                      "idempotencyKey",
                      "use-excess",
                      "allocations",
                      List.of(
                          Map.of(
                              "receiptId",
                              receiptId,
                              "targetType",
                              "SALES_SLIP",
                              "targetId",
                              next.data().path("id").asLong(),
                              "amount",
                              amount - 5000)))));
      assertThat(allocation.status()).isEqualTo(200);
      assertThat(
              get("/api/sales-slips/" + next.data().path("id").asLong())
                  .data()
                  .path("paidAmount")
                  .asLong())
          .isEqualTo(amount - 5000);
      assertThat(
              jdbc.queryForObject(
                  "select unapplied_payment_amount from partner_balance_summaries where partner_id=?",
                  Long.class,
                  partnerId))
          .isZero();
    }
  }

  @Test
  void failedCollectionRollsBackDocumentOutboundAndCreationReceipt() throws Exception {
    var command =
        withReceipt(
            request(SalesType.DIRECT, "출고 완료", 5, null), 0, SalesInitialReceiptInput.Method.CASH);
    var before = snapshot();
    assertThat(
            post(
                    "/api/sales-slips",
                    objectMapper.writeValueAsString(command),
                    Map.of("Idempotency-Key", "overpaid"))
                .status())
        .isEqualTo(400);
    assertThat(snapshot()).isEqualTo(before);
    assertThat(
            post(
                    "/api/sales-slips",
                    objectMapper.writeValueAsString(
                        withReceipt(command, 5000, SalesInitialReceiptInput.Method.CASH)),
                    Map.of("Idempotency-Key", "overpaid"))
                .status())
        .isEqualTo(201);
  }

  @Test
  void rejectsStatusOnlyCompletionAndAuctionInitialCollection() throws Exception {
    var node = (ObjectNode) objectMapper.valueToTree(request(5, null));
    node.put("paymentStatus", "입금 완료");
    assertThat(post("/api/sales-slips", node.toString()).status()).isEqualTo(400);
    var auction =
        withReceipt(
            request(SalesType.AUCTION, "출하 완료", 5, null),
            1000,
            SalesInitialReceiptInput.Method.CASH);
    var before = snapshot();
    assertThat(post("/api/sales-slips", objectMapper.writeValueAsString(auction)).status())
        .isEqualTo(400);
    assertThat(snapshot()).isEqualTo(before);
  }

  private SalesSlipCommand withReceipt(
      SalesSlipCommand request, long amount, SalesInitialReceiptInput.Method method) {
    return new SalesSlipCommand(
        request.saleDate(),
        request.salesType(),
        request.partnerId(),
        request.auctionShipmentId(),
        request.paymentStatus(),
        request.salesStatus(),
        request.paymentMethod(),
        request.memo(),
        request.items(),
        new SalesInitialReceiptInput(amount, DATE, method));
  }

  @ParameterizedTest
  @CsvSource({"DIRECT,작성중", "DIRECT,출고 완료", "AUCTION,작성중", "AUCTION,출하 완료"})
  void replaysLegacyRequestHashAndResponseAfterCancellation(SalesType type, String status)
      throws Exception {
    var request = request(type, status, 5, "original memo");
    var first = creation.create(request, "legacy-v1");
    var legacyMapper =
        JsonMapper.builder()
            .findAndAddModules()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    var legacyHash =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(legacyMapper.writeValueAsBytes(request)));
    assertThat(
            jdbc.queryForObject(
                "select request_fingerprint from sales_creation_receipts where request_key = ?",
                String.class,
                "legacy-v1"))
        .isEqualTo(legacyHash);
    jdbc.update(
        "update sales_creation_receipts set request_fingerprint = ?, response_snapshot = cast(? as jsonb) where request_key = ?",
        legacyHash,
        legacyMapper.writeValueAsString(first),
        "legacy-v1");
    statuses.updateStatus(first.id(), new SalesSlipStatusUpdateRequest("취소", null));
    jdbc.update("update business_partners set is_active = false where id = ?", request.partnerId());
    var before = snapshot();
    try {
      assertThat(creation.create(request, "legacy-v1")).isEqualTo(first);
      var changed =
          new SalesSlipCommand(
              request.saleDate(),
              request.salesType(),
              request.partnerId(),
              request.auctionShipmentId(),
              request.paymentStatus(),
              request.salesStatus(),
              request.paymentMethod(),
              "changed memo",
              request.items());
      assertThatThrownBy(() -> creation.create(changed, "legacy-v1"))
          .isInstanceOf(ConflictException.class)
          .satisfies(
              error ->
                  assertThat(((ConflictException) error).getCode())
                      .isEqualTo("SALES_CREATE_REQUEST_KEY_CONFLICT"));
      assertThat(snapshot()).isEqualTo(before);
    } finally {
      jdbc.update(
          "update business_partners set is_active = true where id = ?", request.partnerId());
    }
  }

  @Test
  void rejectsChangedPayloadForPreviouslyUsedKey() throws Exception {
    assertThat(
            post(
                    "/api/sales-slips",
                    objectMapper.writeValueAsString(request(5, null)),
                    Map.of("Idempotency-Key", "retry-1"))
                .status())
        .isEqualTo(201);
    var changed =
        post(
            "/api/sales-slips",
            objectMapper.writeValueAsString(request(6, "수정")),
            Map.of("Idempotency-Key", "retry-1"));
    assertThat(changed.status()).isEqualTo(409);
    assertThat(changed.body().path("error").path("code").asText())
        .isEqualTo("SALES_CREATE_REQUEST_KEY_CONFLICT");
    assertThat(
            jdbc.queryForObject(
                "select reserved_quantity from orchid_groups where id = ?", Integer.class, groupId))
        .isEqualTo(5);
  }

  @ParameterizedTest
  @CsvSource({"DIRECT,작성중", "DIRECT,출고 완료", "AUCTION,작성중", "AUCTION,출하 완료"})
  void preservesAllCreationFactsAndFirstResponseForEachLifecycle(SalesType type, String status)
      throws Exception {
    var request = request(type, status, 5, null);
    String body = objectMapper.writeValueAsString(request);
    var first = post("/api/sales-slips", body, Map.of("Idempotency-Key", "lifecycle"));
    assertThat(first.status()).isEqualTo(201);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_events where entity_type = 'SALES_SLIP' and entity_id = ? and action = 'CREATED'",
                Integer.class,
                first.data().path("id").asLong()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select reserved_quantity from orchid_groups where id = ?", Integer.class, groupId))
        .isEqualTo(status.equals("작성중") ? 5 : 0);
    var before = snapshot();
    var retry = post("/api/sales-slips", body, Map.of("Idempotency-Key", "lifecycle"));
    assertThat(retry.status()).isEqualTo(201);
    assertThat(retry.data()).isEqualTo(first.data());
    assertThat(snapshot()).isEqualTo(before);
    assertThat(first.data().path("items").get(0).path("allocations").get(0).path("id").asLong())
        .isPositive();
    assertThat(jdbc.queryForObject("select count(*) from sales_slips", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from sales_creation_receipts", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void replaysAfterCancellationAndPartnerDeactivationWithoutCurrentValidation() throws Exception {
    var request = request(5, null);
    var first =
        post(
            "/api/sales-slips",
            objectMapper.writeValueAsString(request),
            Map.of("Idempotency-Key", "cancelled"));
    assertThat(first.status()).isEqualTo(201);
    Long slipId = first.data().path("id").asLong();
    statuses.updateStatus(slipId, new SalesSlipStatusUpdateRequest("취소", null));
    jdbc.update("update business_partners set is_active = false where id = ?", partnerId);
    var before = snapshot();
    var retry =
        post(
            "/api/sales-slips",
            objectMapper.writeValueAsString(request),
            Map.of("Idempotency-Key", "cancelled"));
    assertThat(retry.status()).isEqualTo(201);
    assertThat(retry.data()).isEqualTo(first.data());
    assertThat(retry.data().path("salesStatus").asText()).isEqualTo("작성중");
    assertThat(snapshot()).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "select sales_status from sales_slips where id = ?", String.class, slipId))
        .isEqualTo("취소");
  }

  @Test
  void replaysCompletedSaleAfterAllAvailableStockWasConsumed() throws Exception {
    var request = request(SalesType.DIRECT, "출고 완료", 100, null);
    var first =
        post(
            "/api/sales-slips",
            objectMapper.writeValueAsString(request),
            Map.of("Idempotency-Key", "all-stock"));
    assertThat(first.status()).isEqualTo(201);
    assertThat(
            jdbc.queryForObject(
                "select quantity from orchid_groups where id = ?", Integer.class, groupId))
        .isZero();
    var before = snapshot();
    var retry =
        post(
            "/api/sales-slips",
            objectMapper.writeValueAsString(request),
            Map.of("Idempotency-Key", "all-stock"));
    assertThat(retry.status()).isEqualTo(201);
    assertThat(retry.data()).isEqualTo(first.data());
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void differentKeysPermitIntentionallyIdenticalNewSales() {
    var request = request(5, null);
    var first = creation.create(request, "first");
    var second = creation.create(request, "second");
    assertThat(second.id()).isNotEqualTo(first.id());
    assertThat(jdbc.queryForObject("select count(*) from sales_creation_receipts", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select reserved_quantity from orchid_groups where id = ?", Integer.class, groupId))
        .isEqualTo(10);
  }

  @Test
  void concurrentIndependentCreationsUseDistinctDocumentIdsWithoutDailyNumbering()
      throws Exception {
    var request = request(1, null);
    try (var pool = Executors.newFixedThreadPool(4)) {
      var tasks =
          IntStream.range(0, 4)
              .mapToObj(
                  index ->
                      (Callable<Long>) () -> creation.create(request, "independent-" + index).id())
              .toList();
      var ids = new ArrayList<Long>();
      for (var future : pool.invokeAll(tasks, 30, TimeUnit.SECONDS))
        ids.add(future.get(1, TimeUnit.SECONDS));
      assertThat(ids).hasSize(4).doesNotHaveDuplicates();
      assertThat(jdbc.queryForObject("SELECT count(*) FROM sales_slips", Long.class)).isEqualTo(4);
      assertThat(
              jdbc.queryForObject("SELECT to_regclass('sales_slip_daily_sequences')", String.class))
          .isNull();
    }
  }

  @Test
  void omittedKeysRetainLegacyIndependentCreation() throws Exception {
    String body = objectMapper.writeValueAsString(request(5, null));
    var first = post("/api/sales-slips", body);
    var second = post("/api/sales-slips", body);
    assertThat(first.status()).isEqualTo(201);
    assertThat(second.status()).isEqualTo(201);
    assertThat(second.data().path("id")).isNotEqualTo(first.data().path("id"));
    assertThat(jdbc.queryForObject("select count(*) from sales_creation_receipts", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select reserved_quantity from orchid_groups where id = ?", Integer.class, groupId))
        .isEqualTo(10);
  }

  @Test
  void changedPayloadDoesNotMutateAnyBusinessFact() throws Exception {
    var request = request(5, null);
    creation.create(request, "payload");
    var before = snapshot();
    for (String field :
        List.of("memo", "partnerId", "saleDate", "salesStatus", "items", "salesType")) {
      var changed = objectMapper.valueToTree(request).deepCopy();
      var object = (ObjectNode) changed;
      switch (field) {
        case "memo" -> object.put(field, "변경");
        case "partnerId" -> object.put(field, -1);
        case "saleDate" -> object.put(field, "2044-01-02");
        case "salesStatus" -> object.put(field, "출고 완료");
        case "salesType" -> object.put(field, "AUCTION");
        case "items" -> ((ObjectNode) object.path("items").get(0)).put("spec", "새 규격");
        default -> throw new IllegalStateException(field);
      }
      var result =
          post("/api/sales-slips", object.toString(), Map.of("Idempotency-Key", "payload"));
      assertThat(result.status()).as("field %s: %s", field, result.body()).isEqualTo(409);
      assertThat(result.body().path("error").path("code").asText())
          .isEqualTo("SALES_CREATE_REQUEST_KEY_CONFLICT");
      assertThat(snapshot()).as("unchanged facts after %s", field).isEqualTo(before);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "long"})
  void invalidKeyIsRejectedBeforeCreation(String key) throws Exception {
    if (key.equals("long")) key = "x".repeat(101);
    var before = snapshot();
    var result =
        post(
            "/api/sales-slips",
            objectMapper.writeValueAsString(request(5, null)),
            Map.of("Idempotency-Key", key));
    assertThat(result.status()).isEqualTo(400);
    assertThat(result.body().path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void validationFailureDoesNotConsumeRequestKey() {
    var before = snapshot();
    assertThatThrownBy(() -> creation.create(request(101, null), "retry-failed"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(snapshot()).isEqualTo(before);
    var fixed = creation.create(request(5, null), "retry-failed");
    assertThat(fixed.id()).isPositive();
    assertThat(jdbc.queryForObject("select count(*) from sales_creation_receipts", Integer.class))
        .isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource({"DIRECT,출고 완료", "AUCTION,출하 완료"})
  void lateReceiptFailureRollsBackCompletedSaleAndAllowsSameKeyRetry(
      SalesType type, String status) {
    var request =
        type == SalesType.DIRECT
            ? withReceipt(
                request(type, status, 5, null), 7000, SalesInitialReceiptInput.Method.CASH)
            : request(type, status, 5, null);
    var before = snapshot();
    jdbc.execute(
        "alter table sales_creation_receipts add constraint test_sales_receipt_failure check (response_snapshot is null)");
    try {
      assertThatThrownBy(() -> creation.create(request, "late-failure"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("test_sales_receipt_failure");
      assertThat(snapshot()).isEqualTo(before);
    } finally {
      jdbc.execute(
          "alter table sales_creation_receipts drop constraint test_sales_receipt_failure");
    }
    var success = creation.create(request, "late-failure");
    assertThat(success.salesStatus()).isEqualTo(status);
    assertThat(
            jdbc.queryForObject(
                "select quantity from orchid_groups where id = ?", Integer.class, groupId))
        .isEqualTo(95);
    assertThat(jdbc.queryForObject("select count(*) from sales_slips", Integer.class)).isEqualTo(1);
  }

  @Test
  void replayLoadsOnlyReceiptWithoutReassemblingCurrentEntities() {
    var request = request(5, null);
    var first = creation.create(request, "query");
    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    assertThat(creation.create(request, "query")).isEqualTo(first);
    assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    assertThat(statistics.getEntityLoadCount()).isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource({
    "false,false,false,false",
    "false,true,false,false",
    "true,false,false,false",
    "false,false,true,false",
    "false,true,true,false",
    "true,false,true,false",
    "false,false,false,true",
    "false,true,false,true",
    "true,false,false,true"
  })
  void concurrentCreationWaitsForCommitOrRollbackBeforeDeciding(
      boolean rollback, boolean changed, boolean collect, boolean recordOnly) throws Exception {
    var request =
        recordOnly
            ? recordedRequest(SalesType.DIRECT)
            : collect
                ? withReceipt(request(5, null), 7000, SalesInitialReceiptInput.Method.CASH)
                : request(5, null);
    var competing =
        changed
            ? (collect
                ? withReceipt(request(6, "다른 입력"), 7000, SalesInitialReceiptInput.Method.CASH)
                : request(6, "다른 입력"))
            : request;
    var held = new CountDownLatch(1);
    var resume = new CountDownLatch(1);
    var started = new CountDownLatch(1);
    var winnerPid = new AtomicInteger();
    var loserPid = new AtomicInteger();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var winner =
          pool.submit(
              () -> {
                try {
                  return new Outcome(
                      new TransactionTemplate(transactionManager)
                          .execute(
                              tx -> {
                                winnerPid.set(
                                    jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                                var response = creation.create(request, "concurrent");
                                held.countDown();
                                await(resume);
                                if (rollback)
                                  throw new IllegalStateException("simulated lost transaction");
                                return response;
                              }),
                      null);
                } catch (RuntimeException error) {
                  return new Outcome(null, error);
                }
              });
      try {
        assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
        var loser =
            pool.submit(
                () -> {
                  try {
                    return new Outcome(
                        new TransactionTemplate(transactionManager)
                            .execute(
                                tx -> {
                                  jdbc.execute("set local lock_timeout = '15s'");
                                  loserPid.set(
                                      jdbc.queryForObject(
                                          "select pg_backend_pid()", Integer.class));
                                  started.countDown();
                                  return creation.create(competing, "concurrent");
                                }),
                        null);
                  } catch (RuntimeException error) {
                    return new Outcome(null, error);
                  }
                });
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        boolean blocked = false;
        while (System.nanoTime() < deadline) {
          blocked =
              Boolean.TRUE.equals(
                  jdbc.queryForObject(
                      "select ? = any(pg_blocking_pids(?))",
                      Boolean.class,
                      winnerPid.get(),
                      loserPid.get()));
          if (blocked) break;
          Thread.sleep(25);
        }
        assertThat(blocked).isTrue();
        assertThat(loser.isDone()).isFalse();
        resume.countDown();
        var first = winner.get(20, TimeUnit.SECONDS);
        var second = loser.get(20, TimeUnit.SECONDS);
        if (rollback) {
          assertThat(first.error()).isInstanceOf(IllegalStateException.class);
          assertThat(second.error()).isNull();
        } else if (changed) {
          assertThat(first.error()).isNull();
          assertThat(second.error()).isInstanceOf(ConflictException.class);
          assertThat(((ConflictException) second.error()).getCode())
              .isEqualTo("SALES_CREATE_REQUEST_KEY_CONFLICT");
        } else {
          assertThat(first.error()).isNull();
          assertThat(second.error()).isNull();
          assertThat(second.document()).isEqualTo(first.document());
        }
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from partner_payment_events where event_type='PAYMENT_RECEIVED' and partner_id=?",
                    Integer.class,
                    partnerId))
            .isEqualTo(collect ? 1 : 0);
        assertThat(jdbc.queryForObject("select count(*) from sales_slips", Integer.class))
            .isEqualTo(1);
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from audit_events where entity_type = 'SALES_SLIP' and action = 'CREATED'",
                    Integer.class))
            .isEqualTo(1);
        assertThat(
                jdbc.queryForObject("select count(*) from sales_creation_receipts", Integer.class))
            .isEqualTo(1);
        assertThat(
                jdbc.queryForObject(
                    "select reserved_quantity from orchid_groups where id = ?",
                    Integer.class,
                    groupId))
            .isEqualTo(recordOnly ? 0 : 5);
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from sales_inventory_movements", Integer.class))
            .isEqualTo(recordOnly ? 0 : 1);
      } finally {
        resume.countDown();
      }
    }
  }

  private void await(CountDownLatch latch) {
    try {
      if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout");
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(error);
    }
  }

  private Map<String, List<String>> snapshot() {
    var rows = new LinkedHashMap<String, List<String>>();
    for (String table :
        List.of(
            "sales_creation_receipts",
            "sales_slips",
            "sales_slip_items",
            "direct_sales",
            "direct_sale_prices",
            "sales_slip_item_allocations",
            "sales_orchid_group_snapshots",
            "orchid_groups",
            "orchid_group_mutations",
            "orchid_group_mutation_entries",
            "orchid_group_mutation_relations",
            "sales_inventory_movements",
            "partner_balance_summaries",
            "partner_payment_events",
            "audit_events",
            "auction_shipments",
            "auction_shipment_lots",
            "auction_attempts",
            "auction_result_lines",
            "auction_lot_status_history")) {
      rows.put(
          table,
          jdbc.queryForList(
              "select to_jsonb(row)::text from " + table + " row order by 1", String.class));
    }
    return rows;
  }

  private SalesSlipCommand request(SalesType type, String status, int quantity, String memo) {
    Long id = partnerId;
    if (type == SalesType.AUCTION)
      id =
          partners
              .saveAndFlush(
                  new BusinessPartner(
                      "생성 경매장 " + UUID.randomUUID(),
                      PartnerType.AUCTION_HOUSE,
                      null,
                      null,
                      null,
                      null))
              .getId();
    return new SalesSlipCommand(
        DATE,
        type,
        id,
        null,
        type.defaultPaymentStatus(),
        status,
        type.defaultPaymentMethod(),
        memo,
        List.of(
            new SalesSlipItemInput(
                "E2E 난",
                "팔레놉시스",
                null,
                quantity,
                type == SalesType.DIRECT ? 1000 : 0,
                null,
                List.of(new SalesSlipAllocationInput(groupId, quantity)))));
  }

  private SalesSlipCommand recordedRequest(SalesType type) {
    var source = request(type, type == SalesType.DIRECT ? "출고 완료" : "출하 완료", 150, null);
    return new SalesSlipCommand(
        DATE.minusYears(2),
        type,
        source.partnerId(),
        null,
        type.defaultPaymentStatus(),
        source.salesStatus(),
        type.defaultPaymentMethod(),
        "완료 기록",
        List.of(
            new SalesSlipItemInput(
                "과거 품종",
                "팔레놉시스",
                "A",
                150,
                type == SalesType.DIRECT ? 1000 : 0,
                "과거 품목",
                List.of(),
                10,
                3)),
        null,
        true);
  }

  @ParameterizedTest
  @ValueSource(strings = {"DIRECT", "AUCTION"})
  void completedRecordsAndCancellationNeverChangeFarmStock(SalesType type) throws Exception {
    var request = recordedRequest(type);
    var farm = farmSnapshot();
    var first =
        post(
            "/api/sales-slips",
            objectMapper.writeValueAsString(request),
            Map.of("Idempotency-Key", "record"));
    assertThat(first.status()).isEqualTo(201);
    assertThat(first.data().path("saleDate").asText()).isEqualTo(DATE.minusYears(2).toString());
    assertThat(first.data().path("salesStatus").asText()).isEqualTo(request.salesStatus());
    assertThat(first.data().has("historicalAuctionImport")).isFalse();
    assertThat(first.data().path("items").get(0).path("allocations")).isEmpty();
    assertThat(first.data().path("items").get(0).path("unit").asInt()).isEqualTo(10);
    assertThat(first.data().path("items").get(0).path("boxes").asInt()).isEqualTo(3);
    assertThat(first.data().path("totalAmount").asInt())
        .isEqualTo(type == SalesType.DIRECT ? 150000 : 0);
    assertThat(first.data().path("availableActions").toString())
        .contains("CANCEL")
        .doesNotContain("EDIT", "COMPLETE");
    assertThat(farmSnapshot()).isEqualTo(farm);
    assertThat(
            post(
                    "/api/sales-slips",
                    objectMapper.writeValueAsString(request),
                    Map.of("Idempotency-Key", "record"))
                .data())
        .isEqualTo(first.data());
    assertThat(jdbc.queryForObject("select count(*) from sales_slips", Integer.class)).isEqualTo(1);
    if (type == SalesType.AUCTION) {
      assertThat(first.data().path("auctionShipmentId").asLong()).isPositive();
      assertThat(first.data().path("items").get(0).path("auctionShipmentLotId").asLong())
          .isPositive();
      assertThat(
              jdbc.queryForObject(
                  "select shipped_quantity from auction_shipment_lots", Integer.class))
          .isEqualTo(150);
    } else {
      assertThat(jdbc.queryForObject("select total_amount from direct_sales", Integer.class))
          .isEqualTo(150000);
    }
    long id = first.data().path("id").asLong();
    assertThat(putJson("/api/sales-slips/" + id, objectMapper.writeValueAsString(request)).status())
        .isEqualTo(400);
    var canceled =
        patchJson("/api/sales-slips/" + id + "/sales-status", "{\"salesStatus\":\"취소\"}");
    assertThat(canceled.status()).isEqualTo(200);
    assertThat(canceled.data().path("salesStatus").asText()).isEqualTo("취소");
    assertThat(farmSnapshot()).isEqualTo(farm);
    assertThat(jdbc.queryForObject("select count(*) from auction_shipments", Integer.class))
        .isZero();
    if (type == SalesType.DIRECT)
      assertThat(
              jdbc.queryForObject(
                  "select receivable_balance from partner_balance_summaries where partner_id=?",
                  Long.class,
                  partnerId))
          .isZero();
  }

  @Test
  void recordedAuctionResultsUpdateAmountAndBlockCancellation() throws Exception {
    var request = recordedRequest(SalesType.AUCTION);
    var farm = farmSnapshot();
    var first = creation.create(request, "record-auction-result");
    long lot = first.items().getFirst().auctionShipmentLotId();
    var result =
        post(
            "/api/auction-lots/" + lot + "/results",
            """
        {"idempotencyKey":"record-result","auctionDate":"2042-01-02","attemptNo":1,
         "attemptStatus":"SOLD","resultLines":[{"quantity":150,"unitPrice":2000}]}
        """);
    assertThat(result.status()).isEqualTo(200);
    var detail = get("/api/sales-slips/" + first.id());
    assertThat(detail.data().path("totalAmount").asInt()).isEqualTo(300000);
    assertThat(detail.data().path("availableActions").toString()).doesNotContain("CANCEL");
    var before = snapshot();
    assertThat(
            patchJson(
                    "/api/sales-slips/" + first.id() + "/sales-status", "{\"salesStatus\":\"취소\"}")
                .status())
        .isEqualTo(400);
    assertThat(snapshot()).isEqualTo(before);
    assertThat(farmSnapshot()).isEqualTo(farm);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "key",
        "draft",
        "wrongStatus",
        "allocations",
        "shipment",
        "normal",
        "nullAllocations",
        "quantity",
        "name",
        "partnerType"
      })
  void invalidRecordedRequestsLeaveNoEffects(String violation) throws Exception {
    var body = (ObjectNode) objectMapper.valueToTree(recordedRequest(SalesType.DIRECT));
    switch (violation) {
      case "draft" -> body.put("salesStatus", "작성중");
      case "wrongStatus" -> body.put("salesStatus", "출하 완료");
      case "allocations" ->
          ((ObjectNode) body.path("items").get(0))
              .set(
                  "allocations",
                  objectMapper.valueToTree(
                      List.of(Map.of("orchidGroupId", groupId, "quantity", 150))));
      case "shipment" -> body.put("auctionShipmentId", 123);
      case "normal" -> body.remove("recordOnly");
      case "nullAllocations" -> ((ObjectNode) body.path("items").get(0)).putNull("allocations");
      case "quantity" -> ((ObjectNode) body.path("items").get(0)).put("quantity", 0);
      case "name" -> ((ObjectNode) body.path("items").get(0)).put("itemName", " ");
      case "partnerType" -> body.put("salesType", "AUCTION").put("salesStatus", "출하 완료");
      default -> {}
    }
    var before = snapshot();
    var response =
        post(
            "/api/sales-slips",
            body.toString(),
            violation.equals("key") ? Map.of() : Map.of("Idempotency-Key", "invalid-record"));
    assertThat(response.status()).isEqualTo(400);
    assertThat(response.body().path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"DIRECT", "AUCTION"})
  void recordFailureRollsBackTermsLotsAuditAndReceipt(SalesType type) {
    var request = recordedRequest(type);
    var before = snapshot();
    jdbc.execute(
        "alter table sales_creation_receipts add constraint test_record_failure check (response_snapshot is null)");
    try {
      assertThatThrownBy(() -> creation.create(request, "failed-record"))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(snapshot()).isEqualTo(before);
    } finally {
      jdbc.execute("alter table sales_creation_receipts drop constraint test_record_failure");
    }
    assertThat(creation.create(request, "failed-record").id()).isPositive();
  }

  private Map<String, List<String>> farmSnapshot() {
    var rows = new LinkedHashMap<String, List<String>>();
    for (String table :
        List.of(
            "orchid_groups",
            "orchid_group_mutations",
            "orchid_group_mutation_entries",
            "orchid_group_mutation_relations",
            "sales_inventory_movements",
            "sales_slip_item_allocations",
            "sales_orchid_group_snapshots"))
      rows.put(
          table,
          jdbc.queryForList(
              "select to_jsonb(row)::text from " + table + " row order by 1", String.class));
    return rows;
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void replaysOldResponsesWithTheRemovedImportFieldWithoutRewritingReceipts(boolean imported)
      throws Exception {
    var request = request(5, null);
    creation.create(request, "old-import-field");
    jdbc.update(
        "UPDATE sales_creation_receipts SET response_snapshot=jsonb_set(jsonb_set(response_snapshot,'{historicalAuctionImport}',?::jsonb),'{slipNumber}','\"S20260101-001\"'::jsonb) WHERE request_key=?",
        Boolean.toString(imported),
        "old-import-field");
    var expected =
        (ObjectNode)
            objectMapper.readTree(
                jdbc.queryForObject(
                    "SELECT response_snapshot::text FROM sales_creation_receipts WHERE request_key=?",
                    String.class,
                    "old-import-field"));
    expected.remove("historicalAuctionImport");
    expected.remove("slipNumber");
    var before = snapshot();
    var replay =
        post(
            "/api/sales-slips",
            objectMapper.writeValueAsString(request),
            Map.of("Idempotency-Key", "old-import-field"));
    assertThat(replay.status()).isEqualTo(201);
    assertThat(replay.data().has("historicalAuctionImport")).isFalse();
    assertThat(replay.data().has("slipNumber")).isFalse();
    assertThat(replay.data()).isEqualTo(expected);
    assertThat(snapshot()).isEqualTo(before);
  }

  private record Outcome(SalesSlipDocument document, RuntimeException error) {}

  private SalesSlipCommand request(int quantity, String memo) {
    return new SalesSlipCommand(
        DATE,
        SalesType.DIRECT,
        partnerId,
        null,
        "미입금",
        "작성중",
        null,
        memo,
        List.of(
            new SalesSlipItemInput(
                "E2E 난",
                "팔레놉시스",
                null,
                quantity,
                1000,
                null,
                List.of(new SalesSlipAllocationInput(groupId, quantity)))));
  }
}
