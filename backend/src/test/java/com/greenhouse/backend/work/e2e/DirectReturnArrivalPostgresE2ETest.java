package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.audit.application.AuditEventWriter;
import com.greenhouse.backend.farm.mutation.verification.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.sales.api.document.SalesType;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.document.application.SalesSlipCreationService;
import com.greenhouse.backend.sales.document.application.command.SalesSlipAllocationInput;
import com.greenhouse.backend.sales.document.application.command.SalesSlipCommand;
import com.greenhouse.backend.sales.document.application.command.SalesSlipItemInput;
import com.greenhouse.backend.sales.document.domain.SalesSlip;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
@org.springframework.test.context.TestPropertySource(
    properties = "app.orchid-ledger.writer-version=1.1.0")
class DirectReturnArrivalPostgresE2ETest extends WorkE2ETestBase {
  @Autowired JdbcTemplate jdbc;
  @Autowired SalesSlipCreationService creation;
  @Autowired BusinessPartnerRepository partners;
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean AuditEventWriter audit;

  @Autowired WorkTestDataSeeder seeder;
  @Autowired OrchidGroupLedgerTestFixture ledgerFixture;
  @Autowired OrchidGroupLedgerReconciliationService reconciliation;
  Long zoneId;
  Long varietyId;
  Long partnerId;
  String base = "/api/sales-slips/900/direct-changes";

  @BeforeEach
  void seed() {
    jdbc.execute("TRUNCATE sales_slips, partner_payment_events CONTINUE IDENTITY CASCADE");
    seeder.resetKeepingSequences();
    var fixture = seeder.seedContractScenario();
    zoneId = fixture.bedZoneId();
    varietyId =
        jdbc.queryForObject(
            "SELECT variety_id FROM orchid_groups WHERE id=?", Long.class, fixture.orchidGroupId());
    UUID key = UUID.randomUUID();
    ledgerFixture.seedBaseline(key, LocalDate.of(2026, 10, 7), "1.0.0");
    ledgerFixture.activate(key);
    partnerId =
        partners
            .saveAndFlush(
                new BusinessPartner(
                    "가격 구간 " + UUID.randomUUID(), PartnerType.WHOLESALE, null, null, null, null))
            .getId();
    jdbc.update(
        "INSERT INTO sales_slips(id,slip_number,sale_date,sales_type,partner_id,total_amount,paid_amount,remaining_amount,payment_status,sales_status,created_at,updated_at) VALUES(900,'PRICE-PORTIONS',DATE '2026-10-07','DIRECT',?,1000000,0,1000000,'미입금','출고 완료',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        partnerId);
    jdbc.execute(
        "INSERT INTO sales_slip_items(id,sales_slip_id,item_name,quantity,unit_price,amount) VALUES(901,900,'버드로즈',100,10000,1000000)");
    jdbc.update(
        "INSERT INTO direct_sales(sales_slip_id,partner_id,sale_date,total_amount,created_at,updated_at) VALUES(900,?,DATE '2026-10-07',1000000,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
        partnerId);
    jdbc.execute(
        "INSERT INTO direct_sale_prices(sales_slip_item_id,sales_slip_id,priced_quantity,unit_price,amount) VALUES(901,900,100,10000,1000000)");
  }

  @Test
  void recordOnlyArrivalPreservesInventoryAndBlocksReturnCancellation() throws Exception {
    long returned = returned(20, "return");
    var original = jdbc.queryForList("SELECT id,quantity FROM orchid_groups ORDER BY id");
    long mutations = count("orchid_group_mutations");
    var first = arrive(returned, 10, true, "arrive");
    assertThat(first.status()).as(first.body().toString()).isEqualTo(200);
    long arrivalId = first.data().path("arrivalId").asLong();
    assertThat(arrive(returned, 10, true, "arrive").data()).isEqualTo(first.data());
    assertThat(arrive(returned, 11, true, "arrive").status()).isEqualTo(409);
    var state = get(base + "/return-arrivals").data();
    assertThat(state.path("pendingReturns").get(0).path("pendingQuantity").asInt()).isEqualTo(10);
    assertThat(state.path("history").path("content").get(0).path("recordOnly").asBoolean())
        .isTrue();
    assertThat(
            get(base)
                .data()
                .path("history")
                .path("content")
                .get(0)
                .path("cancelAllowed")
                .asBoolean())
        .isFalse();
    assertThat(cancelReturn(returned, "blocked").body().path("error").path("code").asText())
        .isEqualTo("DIRECT_RETURN_ARRIVAL_EXISTS");
    var cancel = cancelArrival(arrivalId, "cancel");
    assertThat(cancel.status()).as(cancel.body().toString()).isEqualTo(200);
    assertThat(cancelArrival(arrivalId, "cancel").data()).isEqualTo(cancel.data());
    assertThat(cancelReturn(returned, "return-cancel").status()).isEqualTo(200);
    assertThat(arrive(returned, 10, true, "arrive").data()).isEqualTo(first.data());
    assertThat(arrive(returned, 1, true, "late").status()).isEqualTo(409);
    assertThat(jdbc.queryForList("SELECT id,quantity FROM orchid_groups ORDER BY id"))
        .isEqualTo(original);
    assertThat(count("orchid_group_mutations")).isEqualTo(mutations);
    assertThat(count("partner_payment_events")).isZero();
  }

  @Test
  void createsNewFarmGroupAndCompensatesOnlyItsOwnArrival() throws Exception {
    long returned = returned(20, "return");
    long inbound = count("inbound_records");
    var first = arrive(returned, 10, false, "farm-arrive");
    assertThat(first.status()).as(first.body().toString()).isEqualTo(200);
    long arrivalId = first.data().path("arrivalId").asLong();
    var state = get(base + "/return-arrivals").data();
    long group = state.path("history").path("content").get(0).path("orchidGroupId").asLong();
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM orchid_groups WHERE id=?", Integer.class, group))
        .isEqualTo(10);
    assertThat(
            jdbc.queryForObject(
                "SELECT inbound_record_id FROM orchid_groups WHERE id=?", Long.class, group))
        .isNull();
    assertThat(count("inbound_records")).isEqualTo(inbound);
    assertThat(arrive(returned, 10, false, "farm-arrive").data()).isEqualTo(first.data());
    assertThat(arrive(returned, 5, true, "record-arrive").status()).isEqualTo(200);
    assertThat(cancelArrival(arrivalId, "cancel").status()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM orchid_groups WHERE id=?", Integer.class, group))
        .isZero();
    assertThat(
            get(base + "/return-arrivals")
                .data()
                .path("pendingReturns")
                .get(0)
                .path("pendingQuantity")
                .asInt())
        .isEqualTo(15);
    assertThat(get(base).data().path("currentAmount").asInt()).isEqualTo(800000);
    assertThat(count("partner_payment_events")).isZero();
    assertThat(reconciliation.reconcile().issues()).isEmpty();
  }

  @Test
  void invalidQuantityDatesForeignReturnsAndFarmInputRollback() throws Exception {
    long returned = returned(5, "return");
    assertThat(arrive(returned, 6, true, "too-much").status()).isEqualTo(409);
    assertThat(arrive(returned + 100, 1, true, "foreign").status()).isEqualTo(404);
    var body = arrivalBody(1, false);
    body.put("bedZoneId", 99999999L);
    assertThat(
            post(
                    base + "/" + returned + "/arrivals",
                    objectMapper.writeValueAsString(body),
                    Map.of("Idempotency-Key", "bad-zone"))
                .status())
        .isIn(400, 404);
    body = arrivalBody(1, true);
    body.put("arrivalDate", "2026-10-07");
    assertThat(
            post(
                    base + "/" + returned + "/arrivals",
                    objectMapper.writeValueAsString(body),
                    Map.of("Idempotency-Key", "bad-date"))
                .status())
        .isEqualTo(400);
    body = arrivalBody(1, true);
    body.put("bedZoneId", zoneId);
    assertThat(
            post(
                    base + "/" + returned + "/arrivals",
                    objectMapper.writeValueAsString(body),
                    Map.of("Idempotency-Key", "bad-input"))
                .status())
        .isEqualTo(400);
    assertThat(count("direct_return_arrivals")).isZero();
    assertThat(count("direct_return_command_receipts")).isZero();
    assertThat(arrive(returned, 1, true, "bad-zone").status()).isEqualTo(200);
  }

  @Test
  void farmDownstreamUseBlocksCancellationAtomically() throws Exception {
    long returned = returned(10, "return");
    var first = arrive(returned, 10, false, "arrive");
    assertThat(first.status()).as(first.body().toString()).isEqualTo(200);
    long id = first.data().path("arrivalId").asLong();
    long group =
        jdbc.queryForObject(
            "SELECT orchid_group_id FROM direct_return_arrivals WHERE id=?", Long.class, id);
    creation.create(
        new SalesSlipCommand(
            LocalDate.of(2026, 10, 10),
            SalesType.DIRECT,
            partnerId,
            null,
            null,
            SalesSlip.STATUS_DRAFT,
            null,
            null,
            List.of(
                new SalesSlipItemInput(
                    "E2E 난",
                    "팔레놉시스",
                    null,
                    1,
                    1000,
                    null,
                    List.of(new SalesSlipAllocationInput(group, 1))))));
    var cancel = cancelArrival(id, "blocked");
    assertThat(cancel.status()).isEqualTo(409);
    assertThat(cancel.body().path("error").path("code").asText())
        .isEqualTo("DIRECT_ARRIVAL_COMPENSATION_BLOCKED");
    assertThat(
            jdbc.queryForObject(
                "SELECT canceled_at FROM direct_return_arrivals WHERE id=?", Timestamp.class, id))
        .isNull();
    assertThat(
            get(base + "/return-arrivals")
                .data()
                .path("pendingReturns")
                .get(0)
                .path("pendingQuantity")
                .asInt())
        .isZero();
    assertThat(count("direct_return_command_receipts")).isEqualTo(1);
  }

  @Test
  void auditFailureRollsBackFarmArrivalAndCanRetrySameKey() throws Exception {
    long returned = returned(10, "return");
    long groups = count("orchid_groups");
    long mutations = count("orchid_group_mutations");
    Mockito.doThrow(new IllegalStateException("arrival audit failure"))
        .when(audit)
        .record(
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.eq("DIRECT_RETURN_ARRIVAL"),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any());
    try {
      assertThat(arrive(returned, 5, false, "retry").status()).isEqualTo(500);
    } finally {
      Mockito.reset(audit);
    }
    assertThat(count("direct_return_arrivals")).isZero();
    assertThat(count("direct_return_command_receipts")).isZero();
    assertThat(count("orchid_groups")).isEqualTo(groups);
    assertThat(count("orchid_group_mutations")).isEqualTo(mutations);
    assertThat(arrive(returned, 5, false, "retry").status()).isEqualTo(200);
  }

  @Test
  void concurrentArrivalsCannotConsumeSameReturnTwice() throws Exception {
    long returned = returned(10, "return");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      Callable<ApiResult> task =
          () -> {
            start.await();
            return arrive(returned, 6, true, UUID.randomUUID().toString());
          };
      var a = executor.submit(task);
      var b = executor.submit(task);
      start.countDown();
      assertThat(List.of(a.get().status(), b.get().status())).containsExactlyInAnyOrder(200, 409);
    }
    assertThat(count("direct_return_arrivals")).isEqualTo(1);
  }

  @Test
  void concurrentSameKeyAndCancelVsArrivalRemainSerialized() throws Exception {
    long returned = returned(10, "return");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      Callable<ApiResult> task =
          () -> {
            start.await();
            return arrive(returned, 10, true, "same");
          };
      var a = executor.submit(task);
      var b = executor.submit(task);
      start.countDown();
      assertThat(a.get().data()).isEqualTo(b.get().data());
    }
    assertThat(count("direct_return_arrivals")).isEqualTo(1);
    long next = returned(10, "next");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      var a =
          executor.submit(
              () -> {
                start.await();
                return arrive(next, 10, true, "race");
              });
      var b =
          executor.submit(
              () -> {
                start.await();
                return cancelReturn(next, "race-cancel");
              });
      start.countDown();
      assertThat(List.of(a.get().status(), b.get().status())).containsExactlyInAnyOrder(200, 409);
    }
  }

  private long returned(int quantity, String key) throws Exception {
    var response =
        post(
            base + "/returns",
            objectMapper.writeValueAsString(
                Map.of(
                    "itemId",
                    901,
                    "quantity",
                    quantity,
                    "changeDate",
                    "2026-10-08",
                    "reason",
                    "반품")),
            Map.of("Idempotency-Key", key));
    assertThat(response.status()).as(response.body().toString()).isEqualTo(200);
    return response.data().path("changeId").asLong();
  }

  private Map<String, Object> arrivalBody(int quantity, boolean recordOnly) {
    var body = new LinkedHashMap<String, Object>();
    body.put("quantity", quantity);
    body.put("arrivalDate", "2026-10-09");
    body.put("recordOnly", recordOnly);
    if (!recordOnly) {
      body.put("bedZoneId", zoneId);
      body.put(
          "details",
          Map.of(
              "varietyId",
              varietyId,
              "quantity",
              quantity,
              "potSize",
              "3.5치",
              "status",
              "정상",
              "placementType",
              "POT",
              "startPosition",
              5,
              "endPosition",
              6));
    }
    return body;
  }

  private ApiResult arrive(long returnId, int quantity, boolean recordOnly, String key)
      throws Exception {
    return post(
        base + "/" + returnId + "/arrivals",
        objectMapper.writeValueAsString(arrivalBody(quantity, recordOnly)),
        Map.of("Idempotency-Key", key));
  }

  private ApiResult cancelArrival(long id, String key) throws Exception {
    return post(
        base + "/return-arrivals/" + id + "/cancel",
        "{\"correctionDate\":\"2026-10-10\",\"reason\":\"도착 오입력\"}",
        Map.of("Idempotency-Key", key));
  }

  private ApiResult cancelReturn(long id, String key) throws Exception {
    return post(
        base + "/" + id + "/cancel",
        "{\"changeDate\":\"2026-10-10\",\"reason\":\"반품 정정\"}",
        Map.of("Idempotency-Key", key));
  }

  private long count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
  }
}
