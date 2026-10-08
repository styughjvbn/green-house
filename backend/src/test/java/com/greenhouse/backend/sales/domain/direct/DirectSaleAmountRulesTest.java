package com.greenhouse.backend.sales.domain.direct;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DirectSaleAmountRulesTest {
  private static final LocalDate DATE = LocalDate.of(2026, 10, 7);

  @Test
  void usesExistingDocumentAndItemIdentifiersAndExternalAllocationTotals() {
    var sale =
        new DirectSale(
            10L,
            20L,
            DATE,
            DATE.plusDays(3),
            "계좌이체",
            List.of(new DirectSalePrice(30L, 5, 1000), new DirectSalePrice(31L, 2, 3000)));
    assertThat(sale.getDocumentId()).isEqualTo(10);
    assertThat(sale.getTotalAmount()).isEqualTo(11000);
    assertThat(sale.getPrices())
        .extracting(DirectSalePrice::getDocumentItemId)
        .containsExactly(30L, 31L);
    assertThat(sale.remainingAmount(4000)).isEqualTo(7000);
    assertThat(sale.remainingAmount(11000)).isZero();
    assertThat(sale.remainingAmount(12000)).isZero();
    sale.requirePaymentAmount(4000, 7000);
    assertThatThrownBy(() -> sale.requirePaymentAmount(4000, 7001))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("잔액");
    assertThatThrownBy(() -> sale.requirePaymentAmount(4000, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> sale.remainingAmount(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBothPositiveAndNegativeMultiplicationOverflowBeforeChangingAPrice() {
    var price = new DirectSalePrice(30L, 5, 1000);
    assertThatThrownBy(() -> price.change(2, 1500000000))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> price.change(3, 1500000000))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(price.getPricedQuantity()).isEqualTo(5);
    assertThat(price.getUnitPrice()).isEqualTo(1000);
    assertThat(price.getAmount()).isEqualTo(5000);
  }

  @Test
  void rejectsTotalOverflowAndDuplicatePricesAndAllowsAZeroPrice() {
    assertThatThrownBy(
            () ->
                new DirectSale(
                    10L,
                    20L,
                    DATE,
                    DATE,
                    null,
                    List.of(
                        new DirectSalePrice(30L, 1, Integer.MAX_VALUE),
                        new DirectSalePrice(31L, 1, 1))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("전표 금액");
    assertThatThrownBy(
            () ->
                new DirectSale(
                    10L,
                    20L,
                    DATE,
                    DATE,
                    null,
                    List.of(new DirectSalePrice(30L, 1, 1), new DirectSalePrice(30L, 1, 1))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("중복");
    assertThat(new DirectSalePrice(30L, 10, 0).getAmount()).isZero();
    assertThatThrownBy(() -> new DirectSalePrice(30L, -1, 1000))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DirectSalePrice(30L, 0, 1000))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DirectSalePrice(30L, 1, -1000))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void paymentStateComesFromExternalValidAllocationsAtTheStorageLimit() {
    var sale =
        new DirectSale(
            10L, 20L, DATE, null, null, List.of(new DirectSalePrice(30L, 1, Integer.MAX_VALUE)));
    assertThat(DirectSaleAmounts.paymentStatus(sale.getTotalAmount(), BigDecimal.ZERO))
        .isEqualTo("미입금");
    assertThat(DirectSaleAmounts.paymentStatus(sale.getTotalAmount(), BigDecimal.ONE))
        .isEqualTo("부분입금");
    sale.requirePaymentAmount(1, 2147483646L);
    assertThat(sale.remainingAmount(2147483647L)).isZero();
    assertThat(
            DirectSaleAmounts.paymentStatus(sale.getTotalAmount(), BigDecimal.valueOf(2147483647L)))
        .isEqualTo("입금 완료");
    assertThatThrownBy(() -> sale.requirePaymentAmount(1, -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> sale.requirePaymentAmount(2147483647L, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(sale.getTotalAmount()).isEqualTo(Integer.MAX_VALUE);
  }
}
