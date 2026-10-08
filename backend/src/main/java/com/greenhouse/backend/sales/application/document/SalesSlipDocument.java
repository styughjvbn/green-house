package com.greenhouse.backend.sales.application.document;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupState;
import com.greenhouse.backend.sales.application.partner.BusinessPartnerInfo;
import com.greenhouse.backend.sales.domain.document.SalesSlip;
import com.greenhouse.backend.sales.domain.document.SalesSlipAction;
import com.greenhouse.backend.sales.domain.document.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.domain.document.SalesType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Schema(name = "SalesSlipResponse")
public record SalesSlipDocument(
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
    String memo,
    List<SalesSlipDocumentItem> items,
    List<SalesSlipAction> availableActions,
    @Schema(nullable = true, description = "현재 금액 검토 필요 여부. 과거 생성 응답에는 미상일 수 있습니다.")
        Boolean financialReviewRequired) {

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
                    SalesSlipDocumentItem.from(
                        item,
                        allocationsByItemId.getOrDefault(item.getId(), List.of()),
                        states,
                        financial == null ? null : financial.prices().get(item.getId())))
            .toList(),
        availableActions,
        financialReviewRequired);
  }
}
