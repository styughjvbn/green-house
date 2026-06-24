package com.greenhouse.backend.sales.dto;

import com.greenhouse.backend.sales.domain.SalesSlip;
import java.time.LocalDate;
import java.util.List;

public record SalesSlipResponse(
	Long id,
	String slipNumber,
	LocalDate saleDate,
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
