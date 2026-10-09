package com.greenhouse.backend.sales.document.application;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupState;
import com.greenhouse.backend.sales.api.document.SalesSlipAction;
import com.greenhouse.backend.sales.api.document.SalesSlipDocument;
import com.greenhouse.backend.sales.api.partner.BusinessPartnerInfo;
import com.greenhouse.backend.sales.document.domain.SalesSlip;
import com.greenhouse.backend.sales.document.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.document.spi.DirectDocumentAccountingPort;
import java.util.List;
import java.util.Map;

final class SalesSlipDocumentFactory {

  private SalesSlipDocumentFactory() {}

  public static SalesSlipDocument from(
      SalesSlip salesSlip,
      BusinessPartnerInfo partner,
      String auctionMarket,
      Map<Long, List<SalesSlipItemAllocation>> allocationsByItemId,
      Map<Long, OrchidGroupState> states,
      DirectDocumentAccountingPort.FinancialSnapshot financial,
      List<SalesSlipAction> availableActions,
      boolean financialReviewRequired) {
    if (financial != null
        && !financial
            .prices()
            .keySet()
            .containsAll(salesSlip.getItems().stream().map(item -> item.getId()).toList()))
      throw new ConflictException("DIRECT_AMOUNT_SOURCE_MISSING", "일반 판매 품목 가격 자료를 찾을 수 없습니다.");
    return new SalesSlipDocument(
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
        salesSlip.getItems().stream()
            .map(
                item ->
                    SalesSlipDocumentItemFactory.from(
                        item,
                        allocationsByItemId.getOrDefault(item.getId(), List.of()),
                        states,
                        financial == null ? null : financial.prices().get(item.getId())))
            .toList(),
        availableActions,
        financialReviewRequired,
        salesSlip.isHistoricalAuctionImport());
  }
}
