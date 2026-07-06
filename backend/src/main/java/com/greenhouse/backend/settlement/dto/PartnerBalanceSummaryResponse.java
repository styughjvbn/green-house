package com.greenhouse.backend.settlement.dto;

import com.greenhouse.backend.settlement.domain.PartnerBalanceSummary;

public record PartnerBalanceSummaryResponse(
	Long partnerId,
	String partnerName,
	Long creditBalance,
	Long unappliedPaymentAmount,
	Long receivableBalance
) {
	public static PartnerBalanceSummaryResponse from(PartnerBalanceSummary summary) {
		return new PartnerBalanceSummaryResponse(
			summary.getPartner().getId(), summary.getPartner().getName(), summary.getCreditBalance(),
			summary.getUnappliedPaymentAmount(), summary.getReceivableBalance());
	}
}
