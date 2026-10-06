package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.application.SalesSlipCreationService;
import com.greenhouse.backend.sales.application.SalesSlipStatusService;
import com.greenhouse.backend.sales.application.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.application.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.application.document.SalesSlipDocument;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.dto.SalesSlipStatusUpdateRequest;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import jakarta.persistence.EntityManagerFactory;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

  @Test
  void replaysSameCreateRequestWithoutReservingTwice() throws Exception {
    String body = objectMapper.writeValueAsString(request(5, null));
    var first = post("/api/sales-slips", body, Map.of("Idempotency-Key", "retry-1"));
    var second = post("/api/sales-slips", body, Map.of("Idempotency-Key", "retry-1"));
    assertThat(first.status()).isEqualTo(201);
    assertThat(second.status()).isEqualTo(201);
    assertThat(second.data()).isEqualTo(first.data());
    assertThat(
            jdbc.queryForObject(
                "select reserved_quantity from orchid_groups where id = ?", Integer.class, groupId))
        .isEqualTo(5);
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

  @ParameterizedTest
  @ValueSource(strings = {"memo", "partnerId", "saleDate", "salesStatus", "items", "salesType"})
  void changedPayloadDoesNotMutateAnyBusinessFact(String field) throws Exception {
    var request = request(5, null);
    creation.create(request, "payload");
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
    var before = snapshot();
    var result = post("/api/sales-slips", object.toString(), Map.of("Idempotency-Key", "payload"));
    assertThat(result.status()).isEqualTo(409);
    assertThat(result.body().path("error").path("code").asText())
        .isEqualTo("SALES_CREATE_REQUEST_KEY_CONFLICT");
    assertThat(snapshot()).isEqualTo(before);
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
  void validationFailureDoesNotConsumeKeyOrDailyNumber() {
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
    var request = request(type, status, 5, null);
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
  @CsvSource({"false,false", "false,true", "true,false"})
  void concurrentCreationWaitsForCommitOrRollbackBeforeDeciding(boolean rollback, boolean changed)
      throws Exception {
    var request = request(5, null);
    var competing = changed ? request(6, "다른 입력") : request;
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
            .isEqualTo(5);
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from sales_inventory_movements", Integer.class))
            .isEqualTo(1);
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
            "sales_slip_daily_sequences",
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
