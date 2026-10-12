package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.audit.application.AuditEventWriter;
import com.greenhouse.backend.sales.api.document.SalesMetricsApi;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
class DirectSaleChangePostgresE2ETest extends WorkE2ETestBase {
  @Autowired JdbcTemplate jdbc;
  @Autowired BusinessPartnerRepository partners;
  @Autowired SalesMetricsApi metrics;
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean AuditEventWriter audit;

  Long partnerId;
  String base = "/api/sales-slips/900/direct-changes";

  @BeforeEach
  void seed() {
    jdbc.execute("TRUNCATE sales_slips, partner_payment_events CONTINUE IDENTITY CASCADE");
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
  void partialPricesReturnsAndCancellationPreserveOriginalAndStock() throws Exception {
    var original = jdbc.queryForList("SELECT * FROM direct_sale_prices");
    var items = jdbc.queryForList("SELECT * FROM sales_slip_items");
    long mutations = jdbc.queryForObject("SELECT count(*) FROM orchid_group_mutations", Long.class);
    long priceId =
        change("price-adjustments", 20, 8000, null, "price").data().path("changeId").asLong();
    var state = get(base).data();
    assertThat(state.path("currentAmount").asInt()).isEqualTo(960000);
    assertThat(state.path("portions").size()).isEqualTo(2);
    assertThat(state.path("portions").get(0).path("quantity").asInt()).isEqualTo(80);
    var returned = change("returns", 5, null, priceId, "return");
    assertThat(returned.status()).as(returned.body().toString()).isEqualTo(200);
    long returnId = returned.data().path("changeId").asLong();
    assertThat(get(base).data().path("currentAmount").asInt()).isEqualTo(920000);
    var returnedPortion = get(base).data().path("returnedPortions");
    assertThat(returnedPortion.size()).isEqualTo(1);
    assertThat(returnedPortion.get(0).path("quantity").asInt()).isEqualTo(5);
    assertThat(returnedPortion.get(0).path("unitPrice").asInt()).isEqualTo(8000);
    assertThat(returnedPortion.get(0).path("amount").asLong()).isEqualTo(40000);
    assertThat(metrics.varietySales(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
        .singleElement()
        .satisfies(value -> assertThat(value.amount()).isEqualTo(920000));
    assertThat(
            jdbc.queryForObject("SELECT amount FROM sales_slip_items WHERE id=901", Integer.class))
        .isEqualTo(1000000);
    assertThat(get("/api/sales-slips/900").data().path("items").get(0).path("amount").asInt())
        .isEqualTo(1000000);
    var blocked = cancel(priceId, "blocked");
    assertThat(blocked.status()).isEqualTo(409);
    assertThat(blocked.body().path("error").path("code").asText())
        .isEqualTo("DIRECT_CHANGE_CANCEL_BLOCKED");
    assertThat(cancel(returnId, "cancel-return").status()).isEqualTo(200);
    assertThat(get(base).data().path("returnedPortions").isEmpty()).isTrue();
    assertThat(cancel(priceId, "cancel-price").status()).isEqualTo(200);
    assertThat(get(base).data().path("currentAmount").asInt()).isEqualTo(1000000);
    assertThat(get(base + "?page=1&size=2").data().path("history").path("content").size())
        .isEqualTo(2);
    assertThat(jdbc.queryForList("SELECT * FROM direct_sale_prices")).isEqualTo(original);
    assertThat(jdbc.queryForList("SELECT * FROM sales_slip_items")).isEqualTo(items);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM orchid_group_mutations", Long.class))
        .isEqualTo(mutations);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM partner_payment_events", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject("SELECT total_amount FROM sales_slips WHERE id=900", Integer.class))
        .isEqualTo(1000000);
    assertThat(get("/api/sales-slips/900").data().path("availableActions").toString())
        .contains("CANCEL");
    var historyBefore = jdbc.queryForList("SELECT * FROM direct_sale_changes ORDER BY id");
    assertThat(patchJson("/api/sales-slips/900/sales-status", "{\"salesStatus\":\"취소\"}").status())
        .isEqualTo(200);
    assertThat(get("/api/sales-slips/900").data().path("salesStatus").asText()).isEqualTo("취소");
    assertThat(jdbc.queryForList("SELECT * FROM direct_sale_changes ORDER BY id"))
        .isEqualTo(historyBefore);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM orchid_group_mutations", Long.class))
        .isEqualTo(mutations);
  }

  @Test
  void returnedPortionsIncludeAllActiveReturnsRegardlessOfHistoryPage() throws Exception {
    long firstId = 0;
    for (int index = 0; index < 13; index++) {
      var returned = change("returns", 1, null, null, "many-returns-" + index);
      assertThat(returned.status()).isEqualTo(200);
      if (index == 0) firstId = returned.data().path("changeId").asLong();
    }
    assertThat(cancel(firstId, "cancel-old-return").status()).isEqualTo(200);
    var state = get(base + "?page=1&size=2").data();
    assertThat(state.path("history").path("content").size()).isEqualTo(2);
    assertThat(state.path("returnedPortions").size()).isEqualTo(1);
    var portion = state.path("returnedPortions").get(0);
    assertThat(portion.path("quantity").asInt()).isEqualTo(12);
    assertThat(portion.path("unitPrice").asInt()).isEqualTo(10000);
    assertThat(portion.path("amount").asLong()).isEqualTo(120000);
    assertThat(state.path("portions").get(0).path("quantity").asInt()).isEqualTo(88);
    assertThat(state.path("currentAmount").asInt()).isEqualTo(880000);
  }

  @Test
  void acknowledgedRetryReturnsSameIdentityAndChangedInputIsRejected() throws Exception {
    var first = change("price-adjustments", 20, 8000, null, "same-key");
    assertThat(first.status()).as(first.body().toString()).isEqualTo(200);
    assertThat(change("price-adjustments", 20, 8000, null, "same-key").data())
        .isEqualTo(first.data());
    assertThat(change("price-adjustments", 21, 8000, null, "same-key").status()).isEqualTo(409);
    assertThat(change("returns", 20, null, null, "same-key").status()).isEqualTo(409);
    long id = first.data().path("changeId").asLong();
    assertThat(cancel(id, "cancel").status()).isEqualTo(200);
    assertThat(change("price-adjustments", 20, 8000, null, "same-key").data())
        .isEqualTo(first.data());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM direct_sale_changes", Long.class))
        .isEqualTo(2);
  }

  @Test
  void paidSaleReductionCreatesRefundObligationWithoutChangingCash() throws Exception {
    var paid =
        post(
            "/api/sales-slips/900/confirm-payment",
            "{\"amount\":1000000,\"paymentDate\":\"2026-10-07\",\"idempotencyKey\":\"paid\"}");
    assertThat(paid.status()).as(paid.body().toString()).isEqualTo(200);
    var cash = jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id");
    var adjusted = change("price-adjustments", 20, 8000, null, "after-cash");
    assertThat(adjusted.status()).as(adjusted.body().toString()).isEqualTo(200);
    var state = get(base).data();
    assertThat(state.path("refundDueAmount").asLong()).isEqualTo(40000);
    var slip = get("/api/sales-slips/900").data();
    assertThat(slip.path("financialReviewRequired").asBoolean()).isFalse();
    assertThat(slip.path("paidAmount").asLong()).isEqualTo(1000000);
    assertThat(slip.path("remainingAmount").asLong()).isZero();
    assertThat(slip.path("availableActions").toString()).doesNotContain("CONFIRM_PAYMENT");
    assertThat(
            change(
                    "returns",
                    5,
                    null,
                    adjusted.data().path("changeId").asLong(),
                    "returned-after-cash")
                .status())
        .isEqualTo(200);
    assertThat(get(base).data().path("refundDueAmount").asLong()).isEqualTo(80000);
    assertThat(jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id"))
        .isEqualTo(cash);
    assertThat(
            get("/api/sales-slips/page?partnerId=" + partnerId)
                .data()
                .path("content")
                .get(0)
                .path("totalAmount")
                .asInt())
        .isEqualTo(920000);
  }

  @Test
  void fullReturnKeepsAllocationCorrectionAvailableAndSupportsZeroPriceReturns() throws Exception {
    assertThat(
            post(
                    "/api/sales-slips/900/confirm-payment",
                    "{\"amount\":1000000,\"paymentDate\":\"2026-10-07\",\"idempotencyKey\":\"paid-all\"}")
                .status())
        .isEqualTo(200);
    assertThat(change("returns", 100, null, null, "all-returned").status()).isEqualTo(200);
    var state = get(base).data();
    assertThat(state.path("currentAmount").asInt()).isZero();
    assertThat(state.path("portions").isEmpty()).isTrue();
    assertThat(state.path("refundDueAmount").asLong()).isEqualTo(1000000);
    assertThat(get("/api/sales-slips/900").data().path("financialReviewRequired").asBoolean())
        .isFalse();
  }

  @Test
  void zeroPriceReturnStillTracksQuantityWithoutStockOrCash() throws Exception {
    var adjustment = change("price-adjustments", 100, 0, null, "free");
    assertThat(adjustment.status()).isEqualTo(200);
    long source = adjustment.data().path("changeId").asLong();
    assertThat(change("returns", 5, null, source, "free-return").status()).isEqualTo(200);
    var state = get(base).data();
    assertThat(state.path("currentAmount").asInt()).isZero();
    assertThat(state.path("portions").get(0).path("quantity").asInt()).isEqualTo(95);
    assertThat(state.path("history").path("content").get(0).path("amountDelta").asLong()).isZero();
  }

  @Test
  void failureAfterWritesRollsBackHistoryMoneyProjectionAndRetryIdentity() throws Exception {
    var document = jdbc.queryForList("SELECT * FROM sales_slips WHERE id=900");
    var source = jdbc.queryForList("SELECT * FROM direct_sales WHERE sales_slip_id=900");
    Mockito.doThrow(new IllegalStateException("audit unavailable"))
        .when(audit)
        .record(
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.eq("DIRECT_SALE"),
            ArgumentMatchers.eq(900L),
            ArgumentMatchers.anyMap(),
            ArgumentMatchers.anyMap(),
            ArgumentMatchers.anyMap());
    assertThat(change("price-adjustments", 20, 8000, null, "rollback-retry").status())
        .isEqualTo(500);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM direct_sale_changes", Long.class))
        .isZero();
    assertThat(jdbc.queryForList("SELECT * FROM sales_slips WHERE id=900")).isEqualTo(document);
    assertThat(jdbc.queryForList("SELECT * FROM direct_sales WHERE sales_slip_id=900"))
        .isEqualTo(source);
    Mockito.reset(audit);
    assertThat(change("price-adjustments", 20, 8000, null, "rollback-retry").status())
        .isEqualTo(200);
  }

  @Test
  void invalidQuantitiesAndOverflowRollBackBothMoneyAndHistory() throws Exception {
    assertThat(change("returns", 101, null, null, "too-many").status()).isEqualTo(409);
    assertThat(change("price-adjustments", 100, Integer.MAX_VALUE, null, "overflow").status())
        .isEqualTo(400);
    assertThat(change("price-adjustments", 1, 10000, null, "unchanged").status()).isEqualTo(400);
    assertThat(change("returns", 1, 2000, null, "return-price").status()).isEqualTo(400);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM direct_sale_changes", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject("SELECT total_amount FROM sales_slips WHERE id=900", Integer.class))
        .isEqualTo(1000000);
    jdbc.execute("UPDATE direct_sale_prices SET unit_price=9000 WHERE sales_slip_item_id=901");
    assertThat(change("returns", 1, null, null, "bad-price").status()).isEqualTo(409);
  }

  @Test
  void unfinishedCanceledAuctionAndForeignPortionsAreRejected() throws Exception {
    jdbc.execute("UPDATE sales_slips SET sales_status='작성중' WHERE id=900");
    assertThat(get(base).data().path("availableTypes").isEmpty()).isTrue();
    assertThat(change("returns", 1, null, null, "draft").status()).isEqualTo(409);
    jdbc.execute("UPDATE sales_slips SET sales_status='취소' WHERE id=900");
    assertThat(change("returns", 1, null, null, "canceled").status()).isEqualTo(409);
    jdbc.execute("UPDATE sales_slips SET sales_status='출하 완료',sales_type='AUCTION' WHERE id=900");
    assertThat(change("returns", 1, null, null, "auction").status()).isEqualTo(409);
    jdbc.execute("UPDATE sales_slips SET sales_status='출고 완료',sales_type='DIRECT' WHERE id=900");
    assertThat(change("returns", 1, null, 999999L, "foreign").status()).isEqualTo(409);
  }

  @Test
  void concurrentChangesCannotConsumeSameQuantityTwice() throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      Callable<ApiResult> task =
          () -> {
            start.await();
            return change("returns", 60, null, null, UUID.randomUUID().toString());
          };
      var a = executor.submit(task);
      var b = executor.submit(task);
      start.countDown();
      assertThat(List.of(a.get().status(), b.get().status())).containsExactlyInAnyOrder(200, 409);
    }
    assertThat(get(base).data().path("portions").get(0).path("quantity").asInt()).isEqualTo(40);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM direct_sale_changes", Long.class))
        .isEqualTo(1);
  }

  @Test
  void concurrentSameKeyCreatesOnlyOneChange() throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      Callable<ApiResult> task =
          () -> {
            start.await();
            return change("price-adjustments", 20, 8000, null, "parallel-key");
          };
      var a = executor.submit(task);
      var b = executor.submit(task);
      start.countDown();
      var first = a.get();
      var second = b.get();
      assertThat(first.status()).isEqualTo(200);
      assertThat(second.status()).isEqualTo(200);
      assertThat(second.data()).isEqualTo(first.data());
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM direct_sale_changes", Long.class))
        .isEqualTo(1);
  }

  @Test
  void equalOriginalTotalStillBlocksCancellationUntilEveryChangeIsCanceled() throws Exception {
    long lower =
        change("price-adjustments", 10, 9000, null, "lower").data().path("changeId").asLong();
    long higher =
        change("price-adjustments", 10, 11000, null, "higher").data().path("changeId").asLong();
    assertThat(get(base).data().path("currentAmount").asInt()).isEqualTo(1000000);
    assertThat(get("/api/sales-slips/900").data().path("availableActions").toString())
        .doesNotContain("CANCEL");
    assertThat(patchJson("/api/sales-slips/900/sales-status", "{\"salesStatus\":\"취소\"}").status())
        .isEqualTo(409);
    assertThat(cancel(lower, "cancel-lower").status()).isEqualTo(200);
    assertThat(patchJson("/api/sales-slips/900/sales-status", "{\"salesStatus\":\"취소\"}").status())
        .isEqualTo(409);
    assertThat(cancel(higher, "cancel-higher").status()).isEqualTo(200);
    assertThat(get("/api/sales-slips/900").data().path("availableActions").toString())
        .contains("CANCEL");
    assertThat(patchJson("/api/sales-slips/900/sales-status", "{\"salesStatus\":\"취소\"}").status())
        .isEqualTo(200);
  }

  @Test
  void zeroAmountReturnBlocksCancellationUntilItIsCanceled() throws Exception {
    jdbc.execute("UPDATE sales_slip_items SET unit_price=0,amount=0 WHERE id=901");
    jdbc.execute(
        "UPDATE direct_sale_prices SET unit_price=0,amount=0 WHERE sales_slip_item_id=901");
    jdbc.execute("UPDATE direct_sales SET total_amount=0 WHERE sales_slip_id=900");
    jdbc.execute("UPDATE sales_slips SET total_amount=0,remaining_amount=0 WHERE id=900");
    long returned =
        change("returns", 5, null, null, "zero-return").data().path("changeId").asLong();
    assertThat(get(base).data().path("currentAmount").asInt()).isZero();
    assertThat(patchJson("/api/sales-slips/900/sales-status", "{\"salesStatus\":\"취소\"}").status())
        .isEqualTo(409);
    assertThat(cancel(returned, "cancel-zero-return").status()).isEqualTo(200);
    assertThat(get("/api/sales-slips/900").data().path("availableActions").toString())
        .contains("CANCEL");
  }

  @Test
  void paymentStillBlocksAfterAllChangesAreCanceled() throws Exception {
    var adjusted = change("price-adjustments", 10, 9000, null, "adjust");
    assertThat(adjusted.status()).isEqualTo(200);
    assertThat(cancel(adjusted.data().path("changeId").asLong(), "cancel-adjust").status())
        .isEqualTo(200);
    assertThat(
            post(
                    "/api/sales-slips/900/confirm-payment",
                    "{\"amount\":100000,\"paymentDate\":\"2026-10-07\",\"idempotencyKey\":\"paid\"}")
                .status())
        .isEqualTo(200);
    assertThat(get("/api/sales-slips/900").data().path("availableActions").toString())
        .doesNotContain("CANCEL");
    assertThat(patchJson("/api/sales-slips/900/sales-status", "{\"salesStatus\":\"취소\"}").status())
        .isEqualTo(400);
  }

  @Test
  void wholeCancellationAndNewChangeAreSerialized() throws Exception {
    long returned =
        change("returns", 10, null, null, "original-return").data().path("changeId").asLong();
    assertThat(cancel(returned, "cancel-original").status()).isEqualTo(200);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      var cancel =
          executor.submit(
              () -> {
                start.await();
                return patchJson("/api/sales-slips/900/sales-status", "{\"salesStatus\":\"취소\"}");
              });
      var change =
          executor.submit(
              () -> {
                start.await();
                return change("returns", 10, null, null, "new-return");
              });
      start.countDown();
      assertThat(List.of(cancel.get().status(), change.get().status()))
          .containsExactlyInAnyOrder(200, 409);
    }
  }

  private ApiResult change(String operation, int quantity, Integer price, Long source, String key)
      throws Exception {
    var body = new LinkedHashMap<String, Object>();
    body.put("itemId", 901);
    body.put("quantity", quantity);
    body.put("unitPrice", price);
    body.put("sourceChangeId", source);
    body.put("changeDate", "2026-10-08");
    body.put("reason", "품질 조정");
    return post(
        base + "/" + operation,
        objectMapper.writeValueAsString(body),
        Map.of("Idempotency-Key", key));
  }

  private ApiResult cancel(long id, String key) throws Exception {
    return post(
        base + "/" + id + "/cancel",
        "{\"changeDate\":\"2026-10-09\",\"reason\":\"오입력 정정\"}",
        Map.of("Idempotency-Key", key));
  }
}
