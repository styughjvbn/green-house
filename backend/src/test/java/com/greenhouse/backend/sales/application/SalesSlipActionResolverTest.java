package com.greenhouse.backend.sales.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.dto.SalesSlipAction;
import com.greenhouse.backend.settlement.application.PaymentEventReader;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
		SalesSlip salesSlip = mock(SalesSlip.class);
		when(salesSlip.getId()).thenReturn(2L);
		when(salesSlip.getSalesType()).thenReturn(SalesType.AUCTION);
		when(salesSlip.getSalesStatus()).thenReturn(SalesSlip.STATUS_AUCTION_SHIPMENT_COMPLETED);
		when(salesSlip.getAuctionShipmentId()).thenReturn(20L);
		when(auctionCancellationPolicy.findNonCancelableShipmentIds(List.of(20L)))
				.thenReturn(Set.of(20L));

		assertThat(resolver.resolve(salesSlip)).isEmpty();
	}

	private SalesSlip directSalesSlip(Long id, String status, Long remainingAmount) {
		SalesSlip salesSlip = mock(SalesSlip.class);
		when(salesSlip.getId()).thenReturn(id);
		when(salesSlip.getSalesType()).thenReturn(SalesType.DIRECT);
		when(salesSlip.getSalesStatus()).thenReturn(status);
		when(salesSlip.getRemainingAmount()).thenReturn(remainingAmount);
		return salesSlip;
	}
}
