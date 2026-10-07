package com.greenhouse.backend.sales.domain.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.common.exception.ConflictException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuctionProceedsTest {
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 0, 0);

  @Test
  void grossOnlyEvidenceDoesNotBecomeANetReceivable() {
    var proceeds = new AuctionProceeds(1L, "경매장 자료 2026-10-07", 1000L, null, List.of(10L));
    proceeds.confirmMatching(1000, "작업자", NOW);
    assertThat(proceeds.isMatchingConfirmed()).isTrue();
    assertThat(proceeds.getReceivableAmount()).isNull();
    assertThat(proceeds.isPaymentTargetReady()).isFalse();
    assertThatThrownBy(() -> proceeds.requireAllocation(BigDecimal.ZERO, 1))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void preservesProvidedAmountsAndRequiresCompleteMatching() {
    var proceeds = new AuctionProceeds(1L, "원본 자료 참조", 1000L, 930L, List.of(10L, 11L));
    assertThatThrownBy(() -> proceeds.confirmMatching(600, "작업자", NOW))
        .isInstanceOf(ConflictException.class);
    assertThat(proceeds.isMatchingConfirmed()).isFalse();
    assertThat(proceeds.getReportedGrossAmount()).isEqualTo(1000);
    assertThat(proceeds.getReceivableAmount()).isEqualTo(930);
    proceeds.confirmMatching(1000, "작업자", NOW);
    proceeds.confirmMatching(1000, "다른 확인자", NOW.plusDays(1));
    assertThat(proceeds.getConfirmedBy()).isEqualTo("작업자");
    assertThat(proceeds.getConfirmedAt()).isEqualTo(NOW);
    assertThat(proceeds.remainingAmount(BigDecimal.valueOf(400))).isEqualByComparingTo("530");
    proceeds.requireAllocation(BigDecimal.valueOf(400), 530);
    assertThatThrownBy(() -> proceeds.requireAllocation(BigDecimal.valueOf(400), 531))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(proceeds.remainingAmount(BigDecimal.valueOf(1000))).isZero();
  }

  @Test
  void rejectsDuplicateResultReferencesAndDoesNotConfirmWithoutEvidence() {
    assertThatThrownBy(() -> new AuctionProceeds(1L, "자료", 1000L, 1000L, List.of(10L, 10L)))
        .isInstanceOf(IllegalArgumentException.class);
    var proceeds = new AuctionProceeds(1L, null, 1000L, 1000L, List.of(10L));
    assertThatThrownBy(() -> proceeds.confirmMatching(1000, "작업자", NOW))
        .isInstanceOf(ConflictException.class);
    assertThat(proceeds.isPaymentTargetReady()).isFalse();
  }
}
