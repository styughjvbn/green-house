package com.greenhouse.backend.sales.api.document;

import com.greenhouse.backend.sales.api.partner.BusinessPartnerInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

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
        Boolean financialReviewRequired) {}
