package com.greenhouse.backend.farm.orchid.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrchidGroupInvariantTest {

  @Test
  void cannotReviveCreationCanceledGroupThroughGeneralEdit() {
    var group =
        new OrchidGroup(
            null, "팔레놉시스", "호접란", 10, "3.5치", 2, "정상", 1, BigDecimal.ZERO, BigDecimal.ONE);
    group.cancelCreation();
    assertThatThrownBy(
            () ->
                group.updateDetails(
                    "팔레놉시스",
                    "호접란",
                    10,
                    "3.5치",
                    2,
                    "정상",
                    null,
                    null,
                    false,
                    BigDecimal.ZERO,
                    BigDecimal.ONE,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("생성 취소된 난 묶음");
  }

  @Test
  void cannotReduceQuantityBelowReservedQuantityThroughGeneralEdit() {
    var group =
        new OrchidGroup(
            null, "팔레놉시스", "호접란", 10, "3.5치", 2, "정상", 1, BigDecimal.ZERO, BigDecimal.ONE);
    group.reserve(6);

    assertThatThrownBy(
            () ->
                group.updateDetails(
                    "팔레놉시스",
                    "호접란",
                    5,
                    "3.5치",
                    2,
                    "정상",
                    null,
                    null,
                    false,
                    BigDecimal.ZERO,
                    BigDecimal.ONE,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("예약 수량");
  }
}
