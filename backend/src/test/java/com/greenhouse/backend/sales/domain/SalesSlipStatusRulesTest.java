package com.greenhouse.backend.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SalesSlipStatusRulesTest {

	@Test
	void rejectsCanceledOrWrongTypeStatusAtCreation() {
		assertThatThrownBy(() -> slip(SalesType.DIRECT, SalesSlip.STATUS_CANCELED))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> slip(SalesType.DIRECT, SalesSlip.STATUS_AUCTION_SHIPMENT_COMPLETED))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void allowsOnlyTypeSpecificCompletionFromDraft() {
		SalesSlip slip = slip(SalesType.AUCTION, SalesSlip.STATUS_DRAFT);

		assertThatThrownBy(() -> slip.updateSalesStatus(SalesSlip.STATUS_DIRECT_OUTBOUND_COMPLETED))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {SalesSlip.STATUS_DRAFT, SalesSlip.STATUS_DIRECT_OUTBOUND_COMPLETED})
	void directPaymentAvailabilityFollowsRemainingAmount(String status) {
		SalesSlip slip = payableSlip(SalesType.DIRECT, status);
		assertThat(slip.canConfirmPayment()).isTrue();

		slip.recordPayment(30_000L);
		assertThat(slip.getPaidAmount()).isEqualTo(30_000L);
		assertThat(slip.getRemainingAmount()).isEqualTo(70_000L);
		assertThat(slip.getPaymentStatus()).isEqualTo("부분입금");
		assertThat(slip.canConfirmPayment()).isTrue();

		slip.recordPayment(70_000L);
		assertThat(slip.getPaidAmount()).isEqualTo(100_000L);
		assertThat(slip.getRemainingAmount()).isZero();
		assertThat(slip.getPaymentStatus()).isEqualTo("입금 완료");
		assertThat(slip.canConfirmPayment()).isFalse();
		assertThatCode(slip::validatePaymentTarget).doesNotThrowAnyException();
	}

	@Test
	void canceledDirectSlipCannotReceivePayment() {
		SalesSlip slip = payableSlip(SalesType.DIRECT, SalesSlip.STATUS_DRAFT);
		slip.updateSalesStatus(SalesSlip.STATUS_CANCELED);

		assertPaymentRejected(slip, "취소된 전표는 입금을 확인할 수 없습니다.");
	}

	@ParameterizedTest
	@ValueSource(strings = {SalesSlip.STATUS_DRAFT, SalesSlip.STATUS_AUCTION_SHIPMENT_COMPLETED})
	void auctionSlipCannotReceiveDirectPayment(String status) {
		assertPaymentRejected(payableSlip(SalesType.AUCTION, status),
				"경매 판매전표는 경매장 정산에서 입금을 확인해야 합니다.");
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, -1L, 100_001L})
	void invalidAmountDoesNotChangePaymentState(long amount) {
		SalesSlip slip = payableSlip(SalesType.DIRECT, SalesSlip.STATUS_DRAFT);

		assertThatThrownBy(() -> slip.recordPayment(amount)).isInstanceOf(IllegalArgumentException.class);
		assertUnpaid(slip);
		assertThat(slip.canConfirmPayment()).isTrue();
	}

	@Test
	void editCapabilityAndValidationSharePaidAmountAndEventGuards() {
		var slip = payableSlip(SalesType.DIRECT, SalesSlip.STATUS_DRAFT);
		assertThat(slip.canEdit(false)).isTrue();
		assertThatCode(() -> slip.requireEditable(false)).doesNotThrowAnyException();
		assertThat(slip.canEdit(true)).isFalse();
		assertThatThrownBy(() -> slip.requireEditable(true)).isInstanceOf(IllegalArgumentException.class);
		slip.recordPayment(1L);
		assertThat(slip.canEdit(false)).isFalse();
		assertThatThrownBy(() -> slip.requireEditable(false)).isInstanceOf(IllegalArgumentException.class);
	}

	private void assertPaymentRejected(SalesSlip slip, String message) {
		assertThat(slip.canConfirmPayment()).isFalse();
		assertThatThrownBy(slip::validatePaymentTarget)
				.isInstanceOf(IllegalArgumentException.class).hasMessage(message);
		assertThatThrownBy(() -> slip.recordPayment(30_000L))
				.isInstanceOf(IllegalArgumentException.class).hasMessage(message);
		assertUnpaid(slip);
	}

	private void assertUnpaid(SalesSlip slip) {
		assertThat(slip.getPaidAmount()).isZero();
		assertThat(slip.getRemainingAmount()).isEqualTo(100_000L);
		assertThat(slip.getPaymentStatus()).isEqualTo("미입금");
	}

	private SalesSlip payableSlip(SalesType salesType, String status) {
		SalesSlip slip = slip(salesType, status);
		slip.addItem(new SalesSlipItem(null, "카틀레야", null, "A", 10, 10_000, null));
		return slip;
	}

	private SalesSlip slip(SalesType salesType, String status) {
		return new SalesSlip(
				"STATUS-" + salesType + "-" + status,
				LocalDate.of(2026, 8, 1),
				salesType,
				null,
				null,
				"미입금",
				status,
				null,
				null);
	}
}
