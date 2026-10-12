package com.greenhouse.backend.sales.api.document;

import com.greenhouse.backend.sales.api.partner.BusinessPartnerInfo;
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
    @Schema(description = "공통 조회용 판매금액. 일반 판매는 확정 거래금액, 경매는 연결 lot의 누적 낙찰액 합계이며 받을 금액·수납액이 아니다.")
        Integer totalAmount,
    LocalDate expectedPaymentDate,
    Long paidAmount,
    Long remainingAmount,
    String paymentStatus,
    String salesStatus,
    String paymentMethod,
    String memo) {}
