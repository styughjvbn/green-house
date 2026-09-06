package com.greenhouse.backend.sales.dto;

import com.greenhouse.backend.sales.domain.SalesSlip;
import java.time.LocalDate;
import java.util.List;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.partner.dto.BusinessPartnerResponse;
import com.greenhouse.backend.partner.application.BusinessPartnerInfo;
import java.util.Map;

public record SalesSlipResponse(
		Long id,
		String slipNumber,
		LocalDate saleDate,
		SalesType salesType,
		Long auctionShipmentId,
		String auctionMarket,
		BusinessPartnerResponse partner,
		Integer totalAmount,
		LocalDate expectedPaymentDate,
		Long paidAmount,
		Long remainingAmount,
		String paymentStatus,
		String salesStatus,
		String paymentMethod,
		String memo,
		List<SalesSlipItemResponse> items,
		List<SalesSlipAction> availableActions) {

	public static SalesSlipResponse from(
			SalesSlip salesSlip, BusinessPartnerInfo partner, String auctionMarket,
			Map<Long, List<SalesSlipItemAllocation>> allocationsByItemId,
			List<SalesSlipAction> availableActions) {
		return new SalesSlipResponse(
				salesSlip.getId(),
				salesSlip.getSlipNumber(),
				salesSlip.getSaleDate(),
				salesSlip.getSalesType(),
				salesSlip.getAuctionShipmentId(),
				auctionMarket,
				BusinessPartnerResponse.from(partner),
				salesSlip.getTotalAmount(),
				salesSlip.getExpectedPaymentDate(),
				salesSlip.getPaidAmount(),
				salesSlip.getRemainingAmount(),
				salesSlip.getPaymentStatus(),
				salesSlip.getSalesStatus(),
				salesSlip.getPaymentMethod(),
				salesSlip.getMemo(),
				salesSlip.getItems().stream()
						.map(item -> allocationsByItemId == null
								? SalesSlipItemResponse.from(item)
								: SalesSlipItemResponse.from(
										item,
										allocationsByItemId.getOrDefault(item.getId(), List.of())))
						.toList(),
				availableActions);
	}
}
