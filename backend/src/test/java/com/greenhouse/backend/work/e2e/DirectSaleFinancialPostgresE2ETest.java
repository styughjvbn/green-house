package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.sales.application.direct.DirectSaleFinancialReader;
import com.greenhouse.backend.sales.domain.partner.BusinessPartner;
import com.greenhouse.backend.sales.domain.partner.PartnerType;
import com.greenhouse.backend.sales.domain.payment.PartnerPaymentEvent;
import com.greenhouse.backend.sales.domain.payment.PaymentTargetType;
import com.greenhouse.backend.sales.repository.partner.BusinessPartnerRepository;
import com.greenhouse.backend.sales.repository.payment.PartnerPaymentEventRepository;
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
  void derivesCurrentMoneyFromDirectTermsAndPaymentLinksAndIgnoresDocumentSummaries() {
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
  void legacyReviewDisablesMoneyActionsAndRejectsNewCashAndPriceChanges() throws Exception {
    seedSale(1000, 1, 1000, 1000);
    jdbc.execute("UPDATE sales_slips SET remaining_amount=1000 WHERE id=900");
    jdbc.execute(
        "INSERT INTO direct_sale_amount_reconciliations (sales_slip_id,stored_paid_amount,stored_remaining_amount,stored_payment_status,stored_item_amount_sum,confirmed_allocation_amount,total_mismatch,price_mismatch,paid_mismatch,remaining_mismatch,ledger_review_required,signed_amount_review_required) VALUES (900,400,600,'부분입금',1000,0,false,false,true,true,false,false)");
    var source = jdbc.queryForList("SELECT * FROM direct_sales ORDER BY sales_slip_id");
    var document = jdbc.queryForList("SELECT * FROM sales_slips ORDER BY id");
    var evidence =
        jdbc.queryForList(
            "SELECT * FROM direct_sale_amount_reconciliations ORDER BY sales_slip_id");
    var detail = get("/api/sales-slips/900");
    assertThat(detail.status()).isEqualTo(200);
    assertThat(detail.data().path("financialReviewRequired").asBoolean()).isTrue();
    assertThat(detail.data().path("availableActions").toString())
        .doesNotContain("EDIT", "CONFIRM_PAYMENT");
    var blocked =
        post(
            "/api/sales-slips/900/confirm-payment",
            "{\"amount\":100,\"paymentDate\":\"2026-10-07\",\"idempotencyKey\":\"blocked-review\"}");
    assertThat(blocked.status()).isEqualTo(409);
    assertThat(blocked.body().path("error").path("code").asText())
        .isEqualTo("DIRECT_AMOUNT_REVIEW_REQUIRED");
    var edited =
        putJson(
            "/api/sales-slips/900",
            """
      {"saleDate":"2026-10-07","salesType":"DIRECT","partnerId":%d,"paymentStatus":"미입금","salesStatus":"작성중","items":[{"itemName":"과거 가격","quantity":1,"unitPrice":2000,"allocations":[]}]}
      """
                .formatted(partnerId));
    assertThat(edited.status()).as(edited.body().toString()).isEqualTo(409);
    assertThat(edited.body().path("error").path("code").asText())
        .isEqualTo("DIRECT_AMOUNT_REVIEW_REQUIRED");
    assertThat(jdbc.queryForList("SELECT * FROM direct_sales ORDER BY sales_slip_id"))
        .isEqualTo(source);
    assertThat(jdbc.queryForList("SELECT * FROM sales_slips ORDER BY id")).isEqualTo(document);
    assertThat(
            jdbc.queryForList(
                "SELECT * FROM direct_sale_amount_reconciliations ORDER BY sales_slip_id"))
        .isEqualTo(evidence);
    assertThat(events.count()).isZero();
  }

  @Test
  void successfulPaymentStillReplaysWhenHistoricalReviewLaterBlocksNewMoney() throws Exception {
    seedSale(1000, 1, 1000, 1000);
    jdbc.execute("UPDATE sales_slips SET remaining_amount=1000 WHERE id=900");
    String body =
        "{\"amount\":100,\"paymentDate\":\"2026-10-07\",\"idempotencyKey\":\"original-success\"}";
    assertThat(post("/api/sales-slips/900/confirm-payment", body).status()).isEqualTo(200);
    jdbc.execute(
        "INSERT INTO direct_sale_amount_reconciliations (sales_slip_id,stored_paid_amount,stored_remaining_amount,stored_payment_status,stored_item_amount_sum,confirmed_allocation_amount,total_mismatch,price_mismatch,paid_mismatch,remaining_mismatch,ledger_review_required,signed_amount_review_required) VALUES (900,200,800,'부분입금',1000,100,false,false,true,true,false,false)");
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
            "{\"amount\":1001,\"paymentDate\":\"2026-10-07\",\"idempotencyKey\":\"owned-too-much\"}");
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
    assertThat(jdbc.queryForList("SELECT * FROM partner_payment_events ORDER BY id"))
        .isEqualTo(before);
  }

  private void seedSale(int total, int quantity, int unitPrice, int amount) {
    jdbc.update(
        """
        INSERT INTO sales_slips (id, slip_number, sale_date, sales_type, partner_id, total_amount,
            paid_amount, remaining_amount, payment_status, sales_status, created_at, updated_at)
        VALUES (900, 'FINANCIAL-READER', DATE '2026-10-07', 'DIRECT', ?, ?, 0, 0, '미입금', '작성중', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
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
