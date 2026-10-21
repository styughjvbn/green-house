package com.greenhouse.backend.sales.document.domain;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SalesSlipItemUnitTest {
  @Test
  void acceptsOptionalPositiveIntegerUnitsWithoutChangingQuantityOrAmount() {
    for (Integer unit : Arrays.asList(null, 1, 10, Integer.MAX_VALUE)) {
      var item = new SalesSlipItem(null, "품종", "속", "중", 20, 100, null, unit, 2);
      assertThat(item.getUnit()).isEqualTo(unit);
      assertThat(item.getQuantity()).isEqualTo(20);
      assertThat(item.getAmount()).isEqualTo(2000);
    }
  }

  @Test
  void rejectsNonPositiveUnitsDuringCreationAndUpdate() {
    var item = new SalesSlipItem(null, "품종", "속", "중", 20, 100, null, 10, 2);
    for (int unit : new int[] {0, -1}) {
      assertThatThrownBy(() -> new SalesSlipItem(null, "품종", "속", "중", 20, 100, null, unit, 2))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> item.updateDetails("품종", "속", "중", 20, 100, 2000, null, unit, 2))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(item.getUnit()).isEqualTo(10);
    }
  }
}
