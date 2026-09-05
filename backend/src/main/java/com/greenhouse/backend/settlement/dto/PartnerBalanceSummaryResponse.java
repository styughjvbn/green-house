package com.greenhouse.backend.settlement.dto;

import com.greenhouse.backend.settlement.domain.PartnerBalanceSummary;

public record PartnerBalanceSummaryResponse(
		Long partnerId,
		String partnerName,
		Long creditBalance,
		Long unappliedPaymentAmount,
		Long receivableBalance) {
	public static PartnerBalanceSummaryResponse from(PartnerBalanceSummary summary, String partnerName) {
		return new PartnerBalanceSummaryResponse(
				summary.getPartnerId(), partnerName, summary.getCreditBalance(),
				summary.getUnappliedPaymentAmount(), summary.getReceivableBalance());
	}
}
