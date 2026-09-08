package com.greenhouse.backend.sales.application;

import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipAction;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.settlement.application.PaymentEventReader;
import com.greenhouse.backend.settlement.domain.PaymentTargetType;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesSlipActionResolver {

	private final PaymentEventReader paymentEventReader;

	private final AuctionSalesSlipCancellationPolicy auctionCancellationPolicy;

	public List<SalesSlipAction> resolve(SalesSlip salesSlip) {
		return resolveAll(List.of(salesSlip)).getOrDefault(salesSlip.getId(), List.of());
	}

	public Map<Long, List<SalesSlipAction>> resolveAll(List<SalesSlip> salesSlips) {
		List<Long> directSalesSlipIds = salesSlips.stream()
			.filter(salesSlip -> salesSlip.getSalesType() == SalesType.DIRECT)
			.map(SalesSlip::getId)
			.toList();
		Set<Long> paidSalesSlipIds = paymentEventReader.findExistingTargetIds(PaymentTargetType.SALES_SLIP,
				directSalesSlipIds);
		List<Long> auctionShipmentIds = salesSlips.stream()
			.filter(salesSlip -> salesSlip.getSalesType() == SalesType.AUCTION)
			.filter(salesSlip -> salesSlip.getAuctionShipmentId() != null)
			.map(salesSlip -> salesSlip.getAuctionShipmentId())
			.toList();
		Set<Long> nonCancelableShipmentIds = auctionCancellationPolicy.findNonCancelableShipmentIds(auctionShipmentIds);

		Map<Long, List<SalesSlipAction>> actionsBySalesSlipId = new LinkedHashMap<>();
		for (SalesSlip salesSlip : salesSlips) {
			actionsBySalesSlipId.put(salesSlip.getId(), resolve(salesSlip, paidSalesSlipIds, nonCancelableShipmentIds));
		}
		return actionsBySalesSlipId;
	}

	private List<SalesSlipAction> resolve(SalesSlip salesSlip, Set<Long> paidSalesSlipIds,
			Set<Long> nonCancelableShipmentIds) {
		if (salesSlip.isCanceled()) {
			return List.of();
		}

		EnumSet<SalesSlipAction> actions = EnumSet.noneOf(SalesSlipAction.class);
		boolean hasPaymentEvent = paidSalesSlipIds.contains(salesSlip.getId());

		if (salesSlip.canEdit(hasPaymentEvent)) {
			actions.add(SalesSlipAction.EDIT);
		}
		if (salesSlip.canComplete()) {
			actions.add(SalesSlipAction.COMPLETE);
		}
		if (canCancel(salesSlip, hasPaymentEvent, nonCancelableShipmentIds)) {
			actions.add(SalesSlipAction.CANCEL);
		}
		if (salesSlip.canConfirmPayment()) {
			actions.add(SalesSlipAction.CONFIRM_PAYMENT);
		}

		return List.copyOf(actions);
	}

	private boolean canCancel(SalesSlip salesSlip, boolean hasPaymentEvent, Set<Long> nonCancelableShipmentIds) {
		if (salesSlip.getSalesType() == SalesType.DIRECT) {
			return !hasPaymentEvent;
		}
		return salesSlip.getAuctionShipmentId() == null
				|| !nonCancelableShipmentIds.contains(salesSlip.getAuctionShipmentId());
	}

}
