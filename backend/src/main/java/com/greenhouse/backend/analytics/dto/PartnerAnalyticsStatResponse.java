package com.greenhouse.backend.analytics.dto;

import com.greenhouse.backend.partner.domain.PartnerType;
import java.time.LocalDate;

public record PartnerAnalyticsStatResponse(
		Long partnerId,
		String partnerName,
		PartnerType partnerType,
		Long totalSales,
		Long transactionCount,
		Long unpaidAmount,
		Long paidAmount,
		Long receivableBalance,
		Long creditBalance,
		Long unappliedPaymentAmount,
		LocalDate latestSaleDate) {
}
