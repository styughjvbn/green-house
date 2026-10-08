package com.greenhouse.backend.work.correction.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WorkQuantityBalancePolicyTest {

  @Test
  void balancesKnownInputErrorsWithoutInventingGrowth() {
    WorkQuantityBalancePolicy.validate(1200, 1050, 150, 0, false);
    assertThatThrownBy(() -> WorkQuantityBalancePolicy.validate(1000, 1050, 150, 0, false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void allowsGrowthOnlyForAppropriateWorkTypes() {
    WorkQuantityBalancePolicy.validate(1000, 1050, 150, 200, true);
    assertThatThrownBy(() -> WorkQuantityBalancePolicy.validate(1000, 1050, 150, 200, false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsOutOfRangeLossAndGrowthBeforeArithmetic() {
    assertThatThrownBy(
            () -> WorkQuantityBalancePolicy.validate(1, 1, Long.MAX_VALUE, Long.MAX_VALUE, true))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
