package com.greenhouse.backend.sales.dto;

import com.greenhouse.backend.partner.dto.BusinessPartnerResponse;
import com.greenhouse.backend.partner.application.BusinessPartnerInfo;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesType;
import java.time.LocalDate;

public record SalesSlipListItemResponse(
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
		String memo) {

	public static SalesSlipListItemResponse from(SalesSlip salesSlip, BusinessPartnerInfo partner, String auctionMarket) {
		return new SalesSlipListItemResponse(
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
				salesSlip.getMemo());
	}
}
