package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.direct.application.DirectSaleFinancialReader;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import com.greenhouse.backend.sales.payment.domain.PartnerPaymentEvent;
import com.greenhouse.backend.sales.payment.repository.PartnerPaymentEventRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
class DirectSaleFinancialPostgresE2ETest extends WorkE2ETestBase {
  @Autowired private DirectSaleFinancialReader reader;
  @Autowired private BusinessPartnerRepository partners;
  @Autowired private PartnerPaymentEventRepository events;
  @Autowired private JdbcTemplate jdbc;
  private Long partnerId;

  @BeforeEach
  void seed() {
    jdbc.execute("TRUNCATE sales_slips, partner_payment_events CONTINUE IDENTITY CASCADE");
    partnerId =
        partners
            .saveAndFlush(
                new BusinessPartner(
                    "금액 소유권 " + UUID.randomUUID(), PartnerType.WHOLESALE, null, null, null, null))
            .getId();
  }

  @Test
  void derivesCurrentMoneyFromDirectTermsAndPaymentLinksAndIgnoresDocumentSummaries()
      throws Exception {
    seedSale(1000, 1, 1000, 1000);
    var cash =
        events.saveAndFlush(
            PartnerPaymentEvent.received(
                partnerId,
                LocalDate.of(2026, 10, 7),
                400L,
                PaymentTargetType.SALES_SLIP,
                900L,
                null,
                null,
                "financial-reader",
                null,
                "테스트"));
    events.saveAndFlush(PartnerPaymentEvent.manualMatch(cash));
    var before = reader.findAll(List.of(900L)).get(900L);
    assertThat(before.totalAmount()).isEqualTo(1000);
    assertThat(before.allocatedAmount()).isEqualByComparingTo("400");
    assertThat(before.remainingAmount()).isEqualByComparingTo("600");
    assertThat(before.reviewRequired()).isFalse();
    jdbc.execute(
        "UPDATE sales_slips SET total_amount = 700, paid_amount = 700, remaining_amount = 0, payment_status = '입금 완료' WHERE id = 900");
    assertThat(reader.findAll(List.of(900L)).get(900L)).isEqualTo(before);
    jdbc.execute("UPDATE sales_slip_items SET unit_price=1,amount=1 WHERE id=901");
    var storedDocument = jdbc.queryForList("SELECT * FROM sales_slips WHERE id=900");
    var detail = get("/api/sales-slips/900");
    assertThat(detail.status()).as(detail.body().toString()).isEqualTo(200);
    assertThat(detail.data().path("totalAmount").asInt()).isEqualTo(1000);
    assertThat(detail.data().path("paidAmount").asLong()).isEqualTo(400);
    assertThat(detail.data().path("remainingAmount").asLong()).isEqualTo(600);
    assertThat(detail.data().path("paymentStatus").asText()).isEqualTo("부분입금");
    assertThat(detail.data().path("items").get(0).path("unitPrice").asInt()).isEqualTo(1000);
    assertThat(detail.data().path("items").get(0).path("amount").asInt()).isEqualTo(1000);
    assertThat(detail.data().path("availableActions").toString()).contains("CONFIRM_PAYMENT");
    var page = get("/api/sales-slips/page?partnerId=" + partnerId);
    assertThat(page.status()).as(page.body().toString()).isEqualTo(200);
    assertThat(page.data().path("content").get(0).path("totalAmount").asInt()).isEqualTo(1000);
    assertThat(page.data().path("content").get(0).path("paidAmount").asLong()).isEqualTo(400);
    assertThat(jdbc.queryForList("SELECT * FROM sales_slips WHERE id=900"))
        .isEqualTo(storedDocument);
  }

  @Test
  void reportsStoredPriceAndTotalDiscrepanciesWithoutRecalculatingHistoricalAmounts() {
    seedSale(777, 1, 1000, 1000);
    var before = jdbc.queryForList("select * from direct_sales");
    var priceBefore = jdbc.queryForList("select * from direct_sale_prices");
    var financial = reader.findAll(List.of(900L)).get(900L);
    assertThat(financial.totalAmount()).isEqualTo(777);
    assertThat(financial.prices().get(901L).amount()).isEqualTo(1000);
    assertThat(financial.reviewRequired()).isTrue();
    assertThat(jdbc.queryForList("select * from direct_sales")).isEqualTo(before);
    assertThat(jdbc.queryForList("select * from direct_sale_prices")).isEqualTo(priceBefore);
  }

  @Test
  void preservesSignedHistoricalReturnAmountsAndRequiresReview() {
    seedSale(-100, -1, 100, -100);
    var before = jdbc.queryForList("select * from direct_sale_prices");
    var financial = reader.findAll(List.of(900L)).get(900L);
    assertThat(financial.totalAmount()).isEqualTo(-100);
    assertThat(financial.prices().get(901L).amount()).isEqualTo(-100);
    assertThat(financial.allocatedAmount()).isZero();
    assertThat(financial.remainingAmount()).isZero();
    assertThat(financial.reviewRequired()).isTrue();
    assertThat(jdbc.queryForList("select * from direct_sale_prices")).isEqualTo(before);
  }

  @Test
  void excludesAnotherPartnersCashFromValidAllocationsAndPreservesItsLedger() {
    seedSale(1000, 1, 1000, 1000);
    var other =
        partners.saveAndFlush(
            new BusinessPartner(
                "다른 수납 거래처 " + UUID.randomUUID(), PartnerType.WHOLESALE, null, null, null, null));
    var cash =
        events.saveAndFlush(
            PartnerPaymentEvent.received(
                other.getId(),
                LocalDate.of(2026, 10, 7),
                400L,
                PaymentTargetType.SALES_SLIP,
                900L,
                null,
                null,
                "wrong-owner",
                null,
                "테스트"));
    events.saveAndFlush(PartnerPaymentEvent.manualMatch(cash));
    var ledger = jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id");
    var financial = reader.findAll(List.of(900L)).get(900L);
    assertThat(financial.allocatedAmount()).isZero();
    assertThat(financial.remainingAmount()).isEqualByComparingTo("1000");
    assertThat(financial.reviewRequired()).isTrue();
    assertThat(jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id"))
        .isEqualTo(ledger);
  }

  @Test
  void completedSaleWithoutReceiptHistoryDoesNotRequireReviewOrManufactureCash() throws Exception {
    seedSale(1000, 1, 1000, 1000);
    jdbc.execute(
        "UPDATE sales_slips SET paid_amount=1000,remaining_amount=0,payment_status='입금 완료',sales_status='출고 완료' WHERE id=900");
    var document = jdbc.queryForList("SELECT * FROM sales_slips ORDER BY id");
    var detail = get("/api/sales-slips/900");
    assertThat(detail.status()).isEqualTo(200);
    assertThat(detail.data().path("financialReviewRequired").asBoolean()).isFalse();
    assertThat(detail.data().path("paidAmount").asLong()).isZero();
    assertThat(detail.data().path("remainingAmount").asLong()).isEqualTo(1000);
    assertThat(detail.data().path("availableActions").toString()).contains("CONFIRM_PAYMENT");
    assertThat(events.count()).isZero();
    assertThat(jdbc.queryForList("SELECT * FROM sales_slips ORDER BY id")).isEqualTo(document);
    var paid =
        post(
            "/api/sales-slips/900/confirm-payment",
            "{\"amount\":100,\"paymentDate\":\"2026-10-07\",\"idempotencyKey\":\"actual-new-receipt\"}");
    assertThat(paid.status()).isEqualTo(200);
    assertThat(paid.data().path("paidAmount").asLong()).isEqualTo(100);
    assertThat(paid.data().path("financialReviewRequired").asBoolean()).isFalse();
    assertThat(events.count()).isEqualTo(2);
  }

  @Test
  void currentPriceMismatchDisablesMoneyActionsAndRejectsCashAndEditsAtomically() throws Exception {
    seedSale(1000, 1, 1000, 1000);
    jdbc.execute("UPDATE direct_sale_prices SET unit_price=999 WHERE sales_slip_item_id=901");
    var source = jdbc.queryForList("SELECT * FROM direct_sales ORDER BY sales_slip_id");
    var prices = jdbc.queryForList("SELECT * FROM direct_sale_prices ORDER BY sales_slip_item_id");
    var document = jdbc.queryForList("SELECT * FROM sales_slips ORDER BY id");
    var detail = get("/api/sales-slips/900");
    assertThat(detail.status()).isEqualTo(200);
    assertThat(detail.data().path("financialReviewRequired").asBoolean()).isTrue();
    assertThat(detail.data().path("availableActions").toString())
        .doesNotContain("EDIT", "CONFIRM_PAYMENT");
    var blocked =
        post(
            "/api/sales-slips/900/confirm-payment",
            "{\"amount\":100,\"paymentDate\":\"2026-10-07\",\"idempotencyKey\":\"blocked-current-price\"}");
    assertThat(blocked.status()).isEqualTo(409);
    assertThat(blocked.body().path("error").path("code").asText())
        .isEqualTo("DIRECT_AMOUNT_REVIEW_REQUIRED");
    var edited =
        putJson(
            "/api/sales-slips/900",
            """
        {"saleDate":"2026-10-07","salesType":"DIRECT","partnerId":%d,"paymentStatus":"미입금","salesStatus":"작성중",
         "items":[{"itemName":"과거 가격","quantity":1,"unitPrice":2000,"allocations":[]}]}
        """
                .formatted(partnerId));
    assertThat(edited.status()).isEqualTo(409);
    assertThat(edited.body().path("error").path("code").asText())
        .isEqualTo("DIRECT_AMOUNT_REVIEW_REQUIRED");
    assertThat(jdbc.queryForList("SELECT * FROM direct_sales ORDER BY sales_slip_id"))
        .isEqualTo(source);
    assertThat(jdbc.queryForList("SELECT * FROM direct_sale_prices ORDER BY sales_slip_item_id"))
        .isEqualTo(prices);
    assertThat(jdbc.queryForList("SELECT * FROM sales_slips ORDER BY id")).isEqualTo(document);
    assertThat(events.count()).isZero();
  }

  @Test
  void successfulPaymentStillReplaysWhenCurrentPriceReviewLaterBlocksNewMoney() throws Exception {
    seedSale(1000, 1, 1000, 1000);
    jdbc.execute("UPDATE sales_slips SET remaining_amount=1000 WHERE id=900");
    String body =
        "{\"amount\":100,\"paymentDate\":\"2026-10-07\",\"idempotencyKey\":\"original-success\"}";
    assertThat(post("/api/sales-slips/900/confirm-payment", body).status()).isEqualTo(200);
    jdbc.execute("UPDATE direct_sale_prices SET unit_price=999 WHERE sales_slip_item_id=901");
    var before = jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id");
    var replay = post("/api/sales-slips/900/confirm-payment", body);
    assertThat(replay.status()).isEqualTo(200);
    assertThat(replay.data().path("paidAmount").asLong()).isEqualTo(100);
    assertThat(replay.data().path("financialReviewRequired").asBoolean()).isTrue();
    var newMoney =
        post(
            "/api/sales-slips/900/confirm-payment",
            body.replace("original-success", "another-key"));
    assertThat(newMoney.status()).isEqualTo(409);
    assertThat(newMoney.body().path("error").path("code").asText())
        .isEqualTo("DIRECT_AMOUNT_REVIEW_REQUIRED");
    assertThat(jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id"))
        .isEqualTo(before);
  }

  @Test
  void paymentUsesOwnedAmountsDespiteAnIncorrectDocumentSummary() throws Exception {
    seedSale(1000, 1, 1000, 1000);
    jdbc.execute(
        "UPDATE sales_slips SET total_amount=1,paid_amount=1,remaining_amount=0,payment_status='입금 완료' WHERE id=900");
    var rejected =
        post(
            "/api/sales-slips/900/confirm-payment",
            "{\"amount\":0,\"paymentDate\":\"2026-10-07\",\"idempotencyKey\":\"owned-invalid\"}");
    assertThat(rejected.status()).isEqualTo(400);
    assertThat(events.count()).isZero();
    var paid =
        post(
            "/api/sales-slips/900/confirm-payment",
            "{\"amount\":400,\"paymentDate\":\"2026-10-07\",\"idempotencyKey\":\"owned-cash\"}");
    assertThat(paid.status()).as(paid.body().toString()).isEqualTo(200);
    assertThat(paid.data().path("totalAmount").asInt()).isEqualTo(1000);
    assertThat(paid.data().path("paidAmount").asLong()).isEqualTo(400);
    assertThat(paid.data().path("remainingAmount").asLong()).isEqualTo(600);
    assertThat(paid.data().path("paymentStatus").asText()).isEqualTo("부분입금");
    assertThat(events.count()).isEqualTo(2);
    assertThat(reader.findAll(List.of(900L)).get(900L).allocatedAmount())
        .isEqualByComparingTo("400");
    assertThat(
            jdbc.queryForObject(
                "SELECT total_amount FROM direct_sales WHERE sales_slip_id=900", Integer.class))
        .isEqualTo(1000);
    var replay =
        post(
            "/api/sales-slips/900/confirm-payment",
            "{\"amount\":400,\"paymentDate\":\"2026-10-07\",\"idempotencyKey\":\"owned-cash\"}");
    assertThat(replay.status()).isEqualTo(200);
    assertThat(events.count()).isEqualTo(2);
    // Current contract records excess receipts and caps allocation at the owned remaining amount.
    var excess =
        post(
            "/api/sales-slips/900/confirm-payment",
            "{\"amount\":601,\"paymentDate\":\"2026-10-07\",\"idempotencyKey\":\"owned-excess\"}");
    assertThat(excess.status()).isEqualTo(200);
    assertThat(excess.data().path("paidAmount").asLong()).isEqualTo(1000);
    assertThat(excess.data().path("remainingAmount").asLong()).isZero();
    assertThat(reader.findAll(List.of(900L)).get(900L).allocatedAmount())
        .isEqualByComparingTo("1000");
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(unapplied_amount) FROM partner_payment_events WHERE event_type='PAYMENT_RECEIVED'",
                Long.class))
        .isEqualTo(1L);
  }

  @Test
  void currentInvalidLinksBlockNewMoneyWithoutChangingTheLedger() throws Exception {
    seedSale(1000, 1, 1000, 1000);
    events.saveAndFlush(
        PartnerPaymentEvent.received(
            partnerId,
            LocalDate.of(2026, 10, 7),
            400L,
            PaymentTargetType.SALES_SLIP,
            900L,
            null,
            null,
            "unlinked-cash",
            null,
            "기존 담당자"));
    var before = jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id");
    var blocked =
        post(
            "/api/sales-slips/900/confirm-payment",
            "{\"amount\":100,\"paymentDate\":\"2026-10-07\",\"idempotencyKey\":\"invalid-link-cash\"}");
    assertThat(blocked.status()).isEqualTo(409);
    assertThat(blocked.body().path("error").path("code").asText())
        .isEqualTo("DIRECT_AMOUNT_REVIEW_REQUIRED");
    var detail = get("/api/sales-slips/900");
    assertThat(detail.data().path("financialReviewRequired").asBoolean()).isTrue();
    assertThat(detail.data().path("availableActions").toString())
        .doesNotContain("EDIT", "CONFIRM_PAYMENT");
    assertThat(jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id"))
        .isEqualTo(before);
  }

  @Test
  void conflictingDocumentPartnerBlocksNewCashAndMarksTheDetailForReview() throws Exception {
    seedSale(1000, 1, 1000, 1000);
    var other =
        partners.saveAndFlush(
            new BusinessPartner(
                "전표 참조 불일치 " + UUID.randomUUID(), PartnerType.WHOLESALE, null, null, null, null));
    jdbc.update("UPDATE sales_slips SET partner_id=? WHERE id=900", other.getId());
    var detail = get("/api/sales-slips/900");
    assertThat(detail.status()).isEqualTo(200);
    assertThat(detail.data().path("financialReviewRequired").asBoolean()).isTrue();
    assertThat(detail.data().path("availableActions").toString())
        .doesNotContain("EDIT", "CONFIRM_PAYMENT");
    var blocked =
        post(
            "/api/sales-slips/900/confirm-payment",
            "{\"amount\":100,\"paymentDate\":\"2026-10-07\",\"idempotencyKey\":\"wrong-document-party\"}");
    assertThat(blocked.status()).isEqualTo(409);
    assertThat(blocked.body().path("error").path("code").asText())
        .isEqualTo("DIRECT_AMOUNT_REVIEW_REQUIRED");
    assertThat(events.count()).isZero();
  }

  private void seedSale(int total, int quantity, int unitPrice, int amount) {
    jdbc.update(
        """
        INSERT INTO sales_slips (id, sale_date, sales_type, partner_id, total_amount,
            paid_amount, remaining_amount, payment_status, sales_status, created_at, updated_at)
        VALUES (900, DATE '2026-10-07', 'DIRECT', ?, ?, 0, 0, '미입금', '작성중', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        partnerId,
        total);
    jdbc.update(
        """
        INSERT INTO sales_slip_items (id, sales_slip_id, item_name, quantity, unit_price, amount)
        VALUES (901, 900, '과거 가격', ?, ?, ?)
        """,
        quantity,
        unitPrice,
        amount);
    jdbc.update(
        """
        INSERT INTO direct_sales (sales_slip_id, partner_id, sale_date, total_amount, created_at, updated_at)
        VALUES (900, ?, DATE '2026-10-07', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """,
        partnerId,
        total);
    jdbc.update(
        """
        INSERT INTO direct_sale_prices (sales_slip_item_id, sales_slip_id, priced_quantity, unit_price, amount)
        VALUES (901, 900, ?, ?, ?)
        """,
        quantity,
        unitPrice,
        amount);
  }
}
