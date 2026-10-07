package com.greenhouse.backend.sales.dto.payment;

import com.greenhouse.backend.sales.domain.payment.PartnerBalanceSummary;

public record PartnerBalanceSummaryResponse(
    Long partnerId,
    String partnerName,
    Long creditBalance,
    Long unappliedPaymentAmount,
    Long receivableBalance) {
  public static PartnerBalanceSummaryResponse from(
      PartnerBalanceSummary summary, String partnerName) {
    return new PartnerBalanceSummaryResponse(
        summary.getPartnerId(),
        partnerName,
        summary.getCreditBalance(),
        summary.getUnappliedPaymentAmount(),
        summary.getReceivableBalance());
  }
}
