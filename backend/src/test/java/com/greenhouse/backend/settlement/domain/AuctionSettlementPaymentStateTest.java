package com.greenhouse.backend.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AuctionSettlementPaymentStateTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 6, 0, 0);

	@Test
	void rebuildingAndPaymentsAgreeOnRemainingAmountAndStatus() {
		var settlement = new AuctionSettlement(1L, LocalDate.of(2026, 9, 6));
		settlement.synchronizeLines(List.of(), NOW);
		assertThat(settlement.getStatus()).isEqualTo(AuctionSettlementStatus.CREATED);
		var first = new AuctionSettlementLine(1L, 1L, 1, 100, 100L);
		var second = new AuctionSettlementLine(2L, 1L, 1, 50, 50L);
		settlement.synchronizeLines(List.of(first), NOW);
		assertThat(settlement.getStatus()).isEqualTo(AuctionSettlementStatus.PAYMENT_WAITING);

		settlement.recordPayment(30L, "확인자", NOW);
		settlement.synchronizeLines(List.of(first), NOW.plusHours(1));
		assertThat(settlement.getPaidAmount()).isEqualTo(30L);
		assertThat(settlement.getRemainingAmount()).isEqualTo(70L);
		assertThat(settlement.getStatus()).isEqualTo(AuctionSettlementStatus.PARTIALLY_PAID);

		settlement.recordPayment(70L, "확인자", NOW);
		settlement.synchronizeLines(List.of(first), NOW.plusHours(2));
		assertThat(settlement.getRemainingAmount()).isZero();
		assertThat(settlement.getStatus()).isEqualTo(AuctionSettlementStatus.PAID);

		settlement.synchronizeLines(List.of(first, second), NOW.plusHours(3));
		assertThat(settlement.getPaidAmount()).isEqualTo(100L);
		assertThat(settlement.getRemainingAmount()).isEqualTo(50L);
		assertThat(settlement.getStatus()).isEqualTo(AuctionSettlementStatus.PARTIALLY_PAID);
		assertThat(settlement.getConfirmedAt()).isEqualTo(NOW);
	}

	@ParameterizedTest
	@ValueSource(longs = { -1, 0, 101 })
	void invalidPaymentsLeaveAmountsAndConfirmationUntouched(long amount) {
		var settlement = new AuctionSettlement(1L, LocalDate.of(2026, 9, 6));
		settlement.synchronizeLines(List.of(new AuctionSettlementLine(1L, 1L, 1, 100, 100L)), NOW);

		assertThatThrownBy(() -> settlement.recordPayment(amount, "확인자", NOW))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(settlement.getPaidAmount()).isZero();
		assertThat(settlement.getRemainingAmount()).isEqualTo(100L);
		assertThat(settlement.getStatus()).isEqualTo(AuctionSettlementStatus.PAYMENT_WAITING);
		assertThat(settlement.getConfirmedAt()).isNull();
	}
}
