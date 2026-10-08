package com.greenhouse.backend.sales.auction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.common.exception.ConflictException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuctionQuantityHistoryPolicyTest {
  private static final LocalDate DATE = LocalDate.of(2026, 10, 4);
  private static final LocalDateTime AT = DATE.atStartOfDay();

  @Test
  void secondPartialReturnKeepsItsOwnHistoryEvenWhenStatusStaysTheSame() {
    var lot = lot();
    lot.recordResult(DATE, null, AuctionAttemptStatus.FAILED, null, null, null, AT);
    lot.confirmReturn(10, DATE, "작업자", "첫 반환", AT);
    lot.confirmReturn(10, DATE, "작업자", "두 번째 반환", AT.plusSeconds(1));
    assertThat(lot.getStatusHistory()).hasSize(3);
    assertThat(lot.getStatusHistory().getLast().getMemo()).isEqualTo("두 번째 반환");
  }

  @Test
  void sameStatusQuantityCorrectionsKeepSeparateHistory() {
    var lot = lot();
    lot.adjustQuantities(0, 30, 10, "작업자", "첫 보정", AT);
    lot.adjustQuantities(0, 20, 20, "작업자", "두 번째 보정", AT.plusSeconds(1));
    assertThat(lot.getStatusHistory()).hasSize(2);
    assertThat(lot.getStatusHistory().getLast().getMemo()).isEqualTo("두 번째 보정");
  }

  @Test
  void directCorrectionCannotReplaceRecordedAuctionResultQuantities() {
    var lot = lot();
    lot.recordResult(
        DATE,
        null,
        AuctionAttemptStatus.PARTIALLY_SOLD,
        List.of(new AuctionResultLineInput("A", 10, 1000, null, null)),
        null,
        null,
        AT);
    assertThatThrownBy(() -> lot.adjustQuantities(20, 20, 0, "작업자", "잘못된 보정", AT))
        .isInstanceOf(ConflictException.class);
    assertThat(lot.getSoldQuantity()).isEqualTo(10);
  }

  @Test
  void directCorrectionCannotReplaceConfirmedReturnsWithoutAttempts() {
    var lot = lot();
    lot.changeStatus(AuctionLotStatus.REAUCTION_WAITING, "운영 확인", null, null, AT);
    lot.confirmReturn(10, DATE, "작업자", "확인 반환", AT);
    assertThatThrownBy(() -> lot.adjustQuantities(0, 40, 0, "작업자", "반환 삭제", AT))
        .isInstanceOf(ConflictException.class);
    assertThat(lot.getReturnedQuantity()).isEqualTo(10);
  }

  @Test
  void unchangedCorrectionAndUnchangedStatusDoNotCreateDuplicateHistory() {
    var lot = lot();
    lot.changeStatus(AuctionLotStatus.WAITING, "무변경", null, null, AT);
    lot.adjustQuantities(0, 40, 0, null, null, AT);
    assertThat(lot.getCurrentStatus()).isEqualTo(AuctionLotStatus.WAITING);
    assertThat(lot.getStatusHistory()).isEmpty();
    lot.recordResult(
        DATE,
        null,
        AuctionAttemptStatus.SOLD,
        List.of(new AuctionResultLineInput("A", 40, 1000, null, null)),
        null,
        null,
        AT);
    lot.adjustQuantities(40, 0, 0, null, null, AT);
    assertThat(lot.getStatusHistory()).hasSize(1);
  }

  @Test
  void invalidNegativeOrOverflowingTotalsAreRejectedBeforeChangingTheLot() {
    var lot = lot();
    assertThatThrownBy(() -> lot.adjustQuantities(-1, 41, 0, null, null, AT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> lot.adjustQuantities(null, 40, 0, null, null, AT))
        .isInstanceOf(IllegalArgumentException.class);
    // These non-negative int operands sum to 40 only after overflow.
    assertThatThrownBy(
            () -> lot.adjustQuantities(Integer.MAX_VALUE, Integer.MAX_VALUE, 42, null, null, AT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(lot.getWaitingQuantity()).isEqualTo(40);
    assertThat(lot.getStatusHistory()).isEmpty();
  }

  private AuctionShipmentLot lot() {
    return new AuctionShipmentLot("난", "품종", "A", null, 40);
  }
}
