package com.greenhouse.backend.auction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AuctionResultPolicyTest {
	private static final LocalDate DATE = LocalDate.of(2026, 9, 8);

	@ParameterizedTest
	@EnumSource(value = AuctionAttemptStatus.class, names = {"SOLD", "PARTIALLY_SOLD", "FAILED", "RETURN_INFERRED"})
	void keepsResultRowsQuantitiesAndHistory(AuctionAttemptStatus status) {
		var lot = new AuctionShipmentLot("난", "품종", "A", null, 10);
		int sold = status == AuctionAttemptStatus.SOLD ? 10 : status == AuctionAttemptStatus.PARTIALLY_SOLD ? 3 : 0;
		lot.recordResult(DATE, null, status, List.of(line(sold, 100)), null, " memo ", java.time.LocalDateTime.of(2026, 9, 8, 1, 2));
		assertThat(lot.getSoldQuantity()).isEqualTo(sold);
		assertThat(lot.getReturnedQuantity()).isEqualTo(status == AuctionAttemptStatus.RETURN_INFERRED ? 10 : 0);
		assertThat(lot.getAttempts()).singleElement().satisfies(attempt -> {
			assertThat(attempt.getAttemptNo()).isEqualTo(1);
			assertThat(attempt.getAttemptStatus()).isEqualTo(status);
			assertThat(attempt.getMemo()).isEqualTo("memo");
			assertThat(attempt.getResultLines().stream().mapToInt(AuctionResultLine::getQuantity).sum()).isEqualTo(10);
			assertThat(attempt.getResultLines().stream().mapToInt(AuctionResultLine::getAmount).sum()).isEqualTo(sold * 100);
		});
		assertThat(lot.getStatusHistory()).hasSize(1);
	}

	@Test
	void rejectsDuplicateAttemptsAndInvalidTotalsBeforeChangingLot() {
		var lot = new AuctionShipmentLot("난", "품종", "A", null, 10);
		assertThatThrownBy(() -> lot.recordResult(DATE, 1, AuctionAttemptStatus.SOLD, List.of(line(9, 100)), null, null, java.time.LocalDateTime.of(2026, 9, 8, 1, 2)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(lot.getAttempts()).isEmpty();
		lot.recordResult(DATE, 1, AuctionAttemptStatus.FAILED, null, null, null, java.time.LocalDateTime.of(2026, 9, 8, 1, 2));
		assertThatThrownBy(() -> lot.recordResult(DATE, 1, AuctionAttemptStatus.FAILED, null, null, null, java.time.LocalDateTime.of(2026, 9, 8, 1, 2)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("같은 경매일");
		assertThat(lot.getAttempts()).hasSize(1);
		assertThat(lot.getWaitingQuantity()).isEqualTo(10);
	}

	@Test
	void rejectsOverflowAndKeepsPartialReturnRules() {
		var lot = new AuctionShipmentLot("난", "품종", "A", null, 10);
		assertThatThrownBy(() -> lot.recordResult(DATE, null, AuctionAttemptStatus.SOLD,
				List.of(line(10, 1000000000)), null, null, java.time.LocalDateTime.of(2026, 9, 8, 1, 2))).isInstanceOf(IllegalArgumentException.class);
		assertThat(lot.getAttempts()).isEmpty();
		assertThatThrownBy(() -> lot.confirmReturn(1, DATE, null, null, java.time.LocalDateTime.of(2026, 9, 8, 1, 2))).isInstanceOf(IllegalArgumentException.class);
		lot.recordResult(DATE, null, AuctionAttemptStatus.RETURN_INFERRED, null, null, null, java.time.LocalDateTime.of(2026, 9, 8, 1, 2));
		lot.confirmReturn(4, DATE, "담당자", null, java.time.LocalDateTime.of(2026, 9, 8, 1, 2));
		assertThat(lot.getReturnedQuantity()).isEqualTo(4);
		assertThat(lot.getWaitingQuantity()).isEqualTo(6);
		assertThat(lot.getCurrentStatus()).isEqualTo(AuctionLotStatus.PARTIALLY_RETURNED);
	}

	private AuctionResultLineInput line(int quantity, int price) {
		return new AuctionResultLineInput(null, quantity, price, null, null);
	}
}
