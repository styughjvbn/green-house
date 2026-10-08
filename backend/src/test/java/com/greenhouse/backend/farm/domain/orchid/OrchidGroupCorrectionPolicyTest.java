package com.greenhouse.backend.farm.domain.orchid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.farm.structure.domain.BedZone;
import com.greenhouse.backend.farm.structure.domain.BedZoneSide;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrchidGroupCorrectionPolicyTest {

  @Test
  void ordinaryCorrectionCannotAssignCreationCanceledStatus() {
    var group = group();
    assertThatThrownBy(() -> group.correctQuantityAndStatus(0, " 생성 취소 "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("결과 생성 취소");
    assertThat(group.getQuantity()).isEqualTo(10);
    assertThat(group.getStatus()).isEqualTo("정상");
  }

  @Test
  void ordinaryCorrectionCannotReviveACreationCanceledGroup() {
    var group = group();
    group.cancelCreation();
    assertThatThrownBy(() -> group.correctQuantityAndStatus(10, "정상"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("다시 보정할 수 없습니다");
    assertThat(group.getQuantity()).isZero();
    assertThat(group.getStatus()).isEqualTo("생성 취소");
  }

  private OrchidGroup group() {
    return new OrchidGroup(
        new BedZone("보정 구역", BedZoneSide.LEFT, 1),
        "난",
        "보정",
        10,
        "4치",
        2,
        "정상",
        1,
        BigDecimal.ZERO,
        BigDecimal.ONE);
  }
}
