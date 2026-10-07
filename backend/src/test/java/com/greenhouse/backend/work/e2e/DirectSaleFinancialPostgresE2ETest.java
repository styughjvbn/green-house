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
