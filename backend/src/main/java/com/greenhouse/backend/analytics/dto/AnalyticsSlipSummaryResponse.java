package com.greenhouse.backend.analytics.dto;

import java.time.LocalDate;

public record AnalyticsSlipSummaryResponse(
		Long id,
		String slipNumber,
		LocalDate saleDate,
		String partnerName,
		Integer totalAmount,
		Long paidAmount,
		Long remainingAmount,
		String paymentStatus,
		String salesStatus) {
}
