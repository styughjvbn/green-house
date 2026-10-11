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
    @Schema(description = "공통 조회용 판매금액. 일반 판매는 확정 거래금액, 경매는 연결 lot의 누적 낙찰액 합계이며 받을 금액·수납액이 아니다.")
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
        Boolean financialReviewRequired,
    @Schema(
            nullable = true,
            description = "기존 출하로부터 이관한 경매 전표 여부. 재고 근거는 추정 복원하지 않으며 과거 생성 응답에는 미상일 수 있습니다.")
        Boolean historicalAuctionImport) {}
