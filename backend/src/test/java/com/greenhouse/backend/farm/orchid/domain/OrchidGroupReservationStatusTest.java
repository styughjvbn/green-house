package com.greenhouse.backend.farm.orchid.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OrchidGroupReservationStatusTest {

  @ParameterizedTest
  @ValueSource(strings = {"주의", "이상", "병해충", "종료", "폐기", "판매 완료", "생성 취소"})
  void rejectsNewReservationsWithoutChangingAvailableStock(String status) {
    var group = group(status);
    assertThatThrownBy(() -> group.reserve(5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("판매 불가");
    assertThat(group.getQuantity()).isEqualTo(100);
    assertThat(group.getReservedQuantity()).isZero();
    assertThat(group.getAvailableQuantity()).isEqualTo(100);
    assertThat(group.getStatus()).isEqualTo(status);
  }

  @ParameterizedTest
  @ValueSource(strings = {"정상", "분갈이 완료", "기존 사용자 상태"})
  void acceptsReservationsForStatusesOutsideTheUnavailablePolicy(String status) {
    var group = group(status);
    group.reserve(5);
    assertThat(group.getReservedQuantity()).isEqualTo(5);
    assertThat(group.getAvailableQuantity()).isEqualTo(95);
  }

  @ParameterizedTest
  @ValueSource(strings = {"주의", "이상", "병해충"})
  void preservesReleaseAndOutboundForPreviouslyReservedStock(String status) {
    var group = group("정상");
    group.reserve(10);
    group.correctQuantityAndStatus(100, status);
    group.releaseReserved(3);
    group.outboundReserved(7);
    assertThat(group.getReservedQuantity()).isZero();
    assertThat(group.getQuantity()).isEqualTo(93);
    assertThat(group.getStatus()).isEqualTo(status);
    group.restoreOutbound(7);
    assertThat(group.getQuantity()).isEqualTo(100);
    assertThat(group.getStatus()).isEqualTo(status);
  }

  private OrchidGroup group(String status) {
    return new OrchidGroup(
        null, "팔레놉시스", "호접란", 100, "3.5치", 2, status, 1, BigDecimal.ZERO, BigDecimal.ONE);
  }
}
