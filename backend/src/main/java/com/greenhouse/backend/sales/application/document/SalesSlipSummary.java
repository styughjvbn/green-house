package com.greenhouse.backend.sales.application.document;

import com.greenhouse.backend.sales.api.partner.BusinessPartnerInfo;
import com.greenhouse.backend.sales.domain.document.SalesSlip;
import com.greenhouse.backend.sales.domain.document.SalesType;
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
        salesSlip.getMemo());
  }
}
