package com.greenhouse.backend.sales.application.document;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupState;
import com.greenhouse.backend.partner.application.BusinessPartnerInfo;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipAction;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.domain.SalesType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Schema(name = "SalesSlipResponse")
public record SalesSlipDocument(Long id, String slipNumber, LocalDate saleDate, SalesType salesType,
		Long auctionShipmentId, String auctionMarket, BusinessPartnerInfo partner, Integer totalAmount,
		LocalDate expectedPaymentDate, Long paidAmount, Long remainingAmount, String paymentStatus, String salesStatus,
		String paymentMethod, String memo, List<SalesSlipDocumentItem> items, List<SalesSlipAction> availableActions) {

	public static SalesSlipDocument from(SalesSlip salesSlip, BusinessPartnerInfo partner, String auctionMarket,
			Map<Long, List<SalesSlipItemAllocation>> allocationsByItemId, Map<Long, OrchidGroupState> states,
			List<SalesSlipAction> availableActions) {
		return new SalesSlipDocument(salesSlip.getId(), salesSlip.getSlipNumber(), salesSlip.getSaleDate(),
				salesSlip.getSalesType(), salesSlip.getAuctionShipmentId(), auctionMarket, partner,
				salesSlip.getTotalAmount(), salesSlip.getExpectedPaymentDate(), salesSlip.getPaidAmount(),
				salesSlip.getRemainingAmount(), salesSlip.getPaymentStatus(), salesSlip.getSalesStatus(),
				salesSlip.getPaymentMethod(), salesSlip.getMemo(),
				salesSlip.getItems()
					.stream()
					.map(item -> SalesSlipDocumentItem.from(item,
							allocationsByItemId.getOrDefault(item.getId(), List.of()), states))
					.toList(),
				availableActions);
	}
}
