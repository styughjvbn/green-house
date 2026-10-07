package com.greenhouse.backend.sales.domain.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.common.exception.ConflictException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AuctionReturnArrivalTest {
  private static final LocalDate DATE = LocalDate.of(2026, 10, 7);
  private static final LocalDateTime AT = DATE.atStartOfDay();

  @Test
  void actualPartialArrivalBlocksDecisionChangesWhileInferredReturnDoesNot() {
    var lot = new AuctionShipmentLot("난", "품종", null, null, 40);
    lot.applyResult(0, 40, false, true, AT);
    lot.requireFollowUpDecisionChangeAllowed();
    lot.confirmReturn(30, DATE, "확인자", null, AT);
    assertThat(lot.getWaitingQuantity()).isEqualTo(10);
    assertThatThrownBy(lot::requireFollowUpDecisionChangeAllowed)
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void unclassifiedLegacyReturnQuantityDoesNotBecomeANewReturnDecision() {
    var lot = new AuctionShipmentLot("난", "품종", null, null, 40);
    lot.applyResult(10, 5, false, false, AT);
    assertThat(lot.getReturnConfirmedDate()).isNull();
    assertThat(lot.isFollowUpDecisionChangeAllowed(false)).isFalse();
    assertThatThrownBy(
            () -> lot.decideFollowUp(AuctionFollowUpMethod.FARM_RETURN, false, "작업자", "결정", AT))
        .isInstanceOf(ConflictException.class);
    assertThat(lot.getReturnedQuantity()).isEqualTo(5);
  }

  @Test
  void selectingFarmReturnSeparatesInferredQuantityFromActualArrivals() {
    var lot = new AuctionShipmentLot("난", "품종", null, null, 40);
    lot.applyResult(0, 40, false, true, AT);
    assertThat(lot.decideFollowUp(AuctionFollowUpMethod.FARM_RETURN, false, "작업자", "과거 추정 확인", AT))
        .isEqualTo(40);
    assertThat(lot.getInferredReturnQuantity()).isEqualTo(40);
    assertThat(lot.getReturnedQuantity()).isZero();
    assertThat(lot.getWaitingQuantity()).isEqualTo(40);
    assertThat(lot.isActualArrivalAllowed()).isTrue();
  }

  @Test
  void cancellationKeepsActualArrivalAndFarmCreationReferences() {
    var arrival = new AuctionReturnArrival(1L, 2L, 30, DATE, "도착 확인자", AT);
    arrival.linkCreation(10L, 20L);
    arrival.cancel(21L, "잘못된 도착 확인 정정", AT.plusHours(1));
    assertThat(arrival.getQuantity()).isEqualTo(30);
    assertThat(arrival.getArrivalDate()).isEqualTo(DATE);
    assertThat(arrival.getOrchidGroupId()).isEqualTo(10);
    assertThat(arrival.getCreationMutationId()).isEqualTo(20);
    assertThat(arrival.getCancellationMutationId()).isEqualTo(21);
    assertThatThrownBy(() -> arrival.cancel(22L, "재취소", AT.plusHours(2)))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void doesNotAllowCancellationWithoutFarmCompensationOrRelinkingACreation() {
    var arrival = new AuctionReturnArrival(1L, 2L, 30, DATE, "도착 확인자", AT);
    assertThatThrownBy(() -> arrival.cancel(21L, "정정", AT))
        .isInstanceOf(IllegalArgumentException.class);
    arrival.linkCreation(10L, 20L);
    assertThatThrownBy(() -> arrival.linkCreation(11L, 22L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> arrival.cancel(null, "정정", AT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(arrival.getCanceledAt()).isNull();
  }
}
