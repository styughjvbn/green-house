package com.greenhouse.backend.sales.domain.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.support.DirectSaleFixtures;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SalesSlipAmountRulesTest {

  @ParameterizedTest
  @CsvSource({"2,1500000000", "3,1500000000", "2,1073741824"})
  void rejectsItemAmountsBeyondStorageRange(int quantity, int unitPrice) {
    assertThatThrownBy(() -> item(quantity, unitPrice))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("금액");
  }

  @ParameterizedTest
  @CsvSource({"0,100", "-1,100", "1,-100", ",100", "1,"})
  void rejectsInvalidQuantityOrPriceAtTheDomainBoundary(Integer quantity, Integer unitPrice) {
    assertThatThrownBy(() -> item(quantity, unitPrice))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void preservesItemDetailsWhenAnEditOverflows() {
    var item = item(2, 1000);
    assertThatThrownBy(() -> item.updateDetails("변경 품목", "변경 속", "변경 규격", 3, 1500000000, "변경 메모"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(item.getItemName()).isEqualTo("난");
    assertThat(item.getGenus()).isEqualTo("속");
    assertThat(item.getSpec()).isEqualTo("규격");
    assertThat(item.getMemo()).isEqualTo("메모");
    assertThat(item.getQuantity()).isEqualTo(2);
    assertThat(item.getUnitPrice()).isEqualTo(1000);
    assertThat(item.getAmount()).isEqualTo(2000);
  }

  @Test
  void rejectsAnOverflowingTotalBeforeAttachingAnItem() {
    var slip = slip();
    var original = item(1, 1500000000);
    slip.addItem(original);
    var additional = item(1, 1500000000);
    assertThatThrownBy(() -> slip.addItem(additional))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("금액");
    assertThat(slip.getItems()).containsExactly(original);
    assertThat(additional.getSalesSlip()).isNull();
    assertThat(slip.getTotalAmount()).isEqualTo(1500000000);
    assertThat(slip.getRemainingAmount()).isEqualTo(1500000000L);
  }

  @Test
  void rejectsOverflowingReplacementWithoutLosingExistingItems() {
    var slip = slip();
    var original = item(2, 1000);
    slip.addItem(original);
    var replacement = List.of(item(1, Integer.MAX_VALUE), item(1, 1));
    assertThatThrownBy(() -> slip.replaceItems(replacement))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(slip.getItems()).containsExactly(original);
    assertThat(slip.getTotalAmount()).isEqualTo(2000);
    assertThat(slip.getRemainingAmount()).isEqualTo(2000L);
    assertThat(replacement).allSatisfy(item -> assertThat(item.getSalesSlip()).isNull());
  }

  @Test
  void rejectsOverflowWhenRecalculatingEditedItems() {
    var slip = slip();
    var first = item(1, 1000);
    slip.addItem(first);
    slip.addItem(item(1, 1500000000));
    first.updateDetails("난", "속", "규격", 1, 1500000000, "메모");
    assertThatThrownBy(slip::refreshAmounts).isInstanceOf(IllegalArgumentException.class);
    assertThat(slip.getTotalAmount()).isEqualTo(1500001000);
  }

  @Test
  void acceptsTheMaximumTotalAndDisplaysTheOwnedPaymentProjection() {
    var slip = slip();
    slip.addItem(item(2, 1073741823));
    slip.addItem(item(1, 1));
    assertThat(slip.getTotalAmount()).isEqualTo(Integer.MAX_VALUE);
    assertThat(slip.getRemainingAmount()).isEqualTo((long) Integer.MAX_VALUE);
    DirectSaleFixtures.projectAllocation(slip, 1L);
    assertThat(slip.getRemainingAmount()).isEqualTo(2147483646L);
    DirectSaleFixtures.projectAllocation(slip, 2147483647L);
    assertThat(slip.getPaidAmount()).isEqualTo((long) Integer.MAX_VALUE);
    assertThat(slip.getRemainingAmount()).isZero();
  }

  @Test
  void acceptsZeroPriceAndReplacementWithItsOwnItems() {
    var slip = slip();
    var item = item(100, 0);
    slip.addItem(item);
    slip.replaceItems(slip.getItems());
    assertThat(slip.getItems()).containsExactly(item);
    assertThat(slip.getTotalAmount()).isZero();
    assertThat(slip.getRemainingAmount()).isZero();
  }

  private SalesSlipItem item(Integer quantity, Integer unitPrice) {
    return new SalesSlipItem(null, "난", "속", "규격", quantity, unitPrice, "메모");
  }

  private SalesSlip slip() {
    return new SalesSlip(
        "SALE-TEST",
        LocalDate.of(2026, 10, 3),
        SalesType.AUCTION,
        null,
        1L,
        "미입금",
        SalesSlip.STATUS_DRAFT,
        null,
        null);
  }
}
