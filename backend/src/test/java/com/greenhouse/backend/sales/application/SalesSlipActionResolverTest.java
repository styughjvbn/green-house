package com.greenhouse.backend.sales.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.domain.SalesSlipAction;
import com.greenhouse.backend.settlement.application.PaymentEventReader;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SalesSlipActionResolverTest {

	private PaymentEventReader paymentEventReader;
	private AuctionSalesSlipCancellationPolicy auctionCancellationPolicy;
	private SalesSlipActionResolver resolver;

	@BeforeEach
	void setUp() {
		paymentEventReader = mock(PaymentEventReader.class);
		auctionCancellationPolicy = mock(AuctionSalesSlipCancellationPolicy.class);
		resolver = new SalesSlipActionResolver(paymentEventReader, auctionCancellationPolicy);
	}

	@Test
	void allowsEveryDirectDraftActionBeforePayment() {
		SalesSlip salesSlip = directSalesSlip(1L, SalesSlip.STATUS_DRAFT, 100_000L);
		when(paymentEventReader.findExistingTargetIds(PaymentTargetType.SALES_SLIP, List.of(1L)))
				.thenReturn(Set.of());

		assertThat(resolver.resolve(salesSlip)).containsExactly(
				SalesSlipAction.EDIT,
				SalesSlipAction.COMPLETE,
				SalesSlipAction.CANCEL,
				SalesSlipAction.CONFIRM_PAYMENT);
	}

	@Test
	void blocksEditAndCancelAfterDirectPaymentHistoryExists() {
		SalesSlip salesSlip = directSalesSlip(1L, SalesSlip.STATUS_DRAFT, 70_000L);
		when(paymentEventReader.findExistingTargetIds(PaymentTargetType.SALES_SLIP, List.of(1L)))
				.thenReturn(Set.of(1L));

		assertThat(resolver.resolve(salesSlip)).containsExactly(
				SalesSlipAction.COMPLETE,
				SalesSlipAction.CONFIRM_PAYMENT);
	}

	@Test
	void blocksAuctionCancellationAfterResultOrSettlementProgress() {
		SalesSlip salesSlip = salesSlip(2L, SalesType.AUCTION, SalesSlip.STATUS_AUCTION_SHIPMENT_COMPLETED);
		salesSlip.assignAuctionShipment(20L);
		when(auctionCancellationPolicy.findNonCancelableShipmentIds(List.of(20L)))
				.thenReturn(Set.of(20L));

		assertThat(resolver.resolve(salesSlip)).isEmpty();
	}

	private SalesSlip directSalesSlip(Long id, String status, Long remainingAmount) {
		SalesSlip salesSlip = salesSlip(id, SalesType.DIRECT, status);
		if (remainingAmount < 100_000L) {
			salesSlip.recordPayment(100_000L - remainingAmount);
		}
		return salesSlip;
	}

	private SalesSlip salesSlip(Long id, SalesType salesType, String status) {
		SalesSlip salesSlip = new SalesSlip("ACTION-" + id, LocalDate.of(2026, 8, 1), salesType,
				null, 1L, "미입금", status, null, null);
		ReflectionTestUtils.setField(salesSlip, "id", id);
		salesSlip.addItem(new SalesSlipItem(null, "카틀레야", null, "A", 10, 10_000, null));
		return salesSlip;
	}
}
