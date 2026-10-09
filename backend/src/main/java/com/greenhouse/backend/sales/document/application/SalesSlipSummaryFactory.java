package com.greenhouse.backend.sales.document.application;

import com.greenhouse.backend.sales.api.document.SalesSlipSummary;
import com.greenhouse.backend.sales.api.partner.BusinessPartnerInfo;
import com.greenhouse.backend.sales.document.domain.SalesSlip;
import com.greenhouse.backend.sales.document.spi.DirectDocumentAccountingPort;

final class SalesSlipSummaryFactory {

  private SalesSlipSummaryFactory() {}

  public static SalesSlipSummary from(
      SalesSlip salesSlip,
      BusinessPartnerInfo partner,
      String auctionMarket,
      DirectDocumentAccountingPort.FinancialSnapshot financial) {
    return new SalesSlipSummary(
        salesSlip.getId(),
        salesSlip.getSlipNumber(),
        salesSlip.getSaleDate(),
        salesSlip.getSalesType(),
        salesSlip.getAuctionShipmentId(),
        auctionMarket,
        partner,
        financial == null ? salesSlip.getTotalAmount() : financial.totalAmount(),
        financial == null ? salesSlip.getExpectedPaymentDate() : financial.expectedPaymentDate(),
        financial == null
            ? salesSlip.getPaidAmount()
            : financial.allocatedAmount().longValueExact(),
        financial == null
            ? salesSlip.getRemainingAmount()
            : financial.remainingAmount().longValueExact(),
        financial == null ? salesSlip.getPaymentStatus() : financial.paymentStatus(),
        salesSlip.getSalesStatus(),
        financial == null ? salesSlip.getPaymentMethod() : financial.paymentMethod(),
        salesSlip.getMemo(),
        salesSlip.isHistoricalAuctionImport());
  }
}
