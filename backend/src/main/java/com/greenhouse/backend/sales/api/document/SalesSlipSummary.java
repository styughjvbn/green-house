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
    Integer totalAmount,
    LocalDate expectedPaymentDate,
    Long paidAmount,
    Long remainingAmount,
    String paymentStatus,
    String salesStatus,
    String paymentMethod,
    String memo) {}
