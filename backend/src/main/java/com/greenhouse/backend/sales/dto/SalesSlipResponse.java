package com.greenhouse.backend.sales.dto;

import com.greenhouse.backend.sales.domain.SalesSlip;
import java.time.LocalDate;
import java.util.List;
import com.greenhouse.backend.sales.domain.SalesType;

public record SalesSlipResponse(
	Long id,
	String slipNumber,
	LocalDate saleDate,
	SalesType salesType,
	Long auctionShipmentId,
	String auctionMarket,
	CustomerResponse customer,
	Integer totalAmount,
	String paymentStatus,
	String salesStatus,
	String paymentMethod,
	String memo,
	List<SalesSlipItemResponse> items
) {

	public static SalesSlipResponse from(SalesSlip salesSlip) {
		return new SalesSlipResponse(
			salesSlip.getId(),
			salesSlip.getSlipNumber(),
			salesSlip.getSaleDate(),
			salesSlip.getSalesType(),
			salesSlip.getAuctionShipment() == null ? null : salesSlip.getAuctionShipment().getId(),
			salesSlip.getAuctionShipment() == null ? null : salesSlip.getAuctionShipment().getAuctionMarket(),
			CustomerResponse.from(salesSlip.getCustomer()),
			salesSlip.getTotalAmount(),
			salesSlip.getPaymentStatus(),
			salesSlip.getSalesStatus(),
			salesSlip.getPaymentMethod(),
			salesSlip.getMemo(),
			salesSlip.getItems().stream().map(SalesSlipItemResponse::from).toList()
		);
	}
}
