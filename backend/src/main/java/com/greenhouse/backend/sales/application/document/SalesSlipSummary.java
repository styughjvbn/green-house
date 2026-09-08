package com.greenhouse.backend.sales.application.document;

import com.greenhouse.backend.partner.application.BusinessPartnerInfo;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(name = "SalesSlipListItemResponse")
public record SalesSlipSummary(
		Long id,
		String slipNumber,
		LocalDate saleDate,
		SalesType salesType,
		Long auctionShipmentId,
		String auctionMarket,
		BusinessPartnerInfo partner,
		Integer totalAmount,
		LocalDate expectedPaymentDate,
		Long paidAmount,
		Long remainingAmount,
		String paymentStatus,
		String salesStatus,
		String paymentMethod,
		String memo) {

	public static SalesSlipSummary from(SalesSlip salesSlip, BusinessPartnerInfo partner, String auctionMarket) {
		return new SalesSlipSummary(
				salesSlip.getId(),
				salesSlip.getSlipNumber(),
				salesSlip.getSaleDate(),
				salesSlip.getSalesType(),
				salesSlip.getAuctionShipmentId(),
				auctionMarket,
				partner,
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
