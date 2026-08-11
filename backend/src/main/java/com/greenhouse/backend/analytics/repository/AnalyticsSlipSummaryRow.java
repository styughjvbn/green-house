package com.greenhouse.backend.analytics.repository;

import java.time.LocalDate;

public record AnalyticsSlipSummaryRow(
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
