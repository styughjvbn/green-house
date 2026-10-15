package com.greenhouse.backend.sales.document.application.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.greenhouse.backend.sales.api.document.SalesType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "SalesSlipCreateRequest")
public record SalesSlipCommand(
    @NotNull LocalDate saleDate,
    SalesType salesType,
    Long partnerId,
    Long auctionShipmentId,
    @Size(max = 50) String paymentStatus,
    @Size(max = 50) String salesStatus,
    @Size(max = 50) String paymentMethod,
    @Size(max = 1000) String memo,
    @NotNull List<@Valid SalesSlipItemInput> items,
    @Valid @JsonInclude(JsonInclude.Include.NON_NULL) SalesInitialReceiptInput initialReceipt,
    @Schema(description = "이미 완료된 판매·출하를 재고 변경 없이 기록합니다. 완료 상태와 빈 allocation이 필요하며 생성 요청 키는 필수입니다.")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean recordOnly,
    @Valid @JsonInclude(JsonInclude.Include.NON_NULL) SalesSlipPhotoInput sourcePhoto) {
  public SalesSlipCommand(
      LocalDate saleDate,
      SalesType salesType,
      Long partnerId,
      Long auctionShipmentId,
      String paymentStatus,
      String salesStatus,
      String paymentMethod,
      String memo,
      List<SalesSlipItemInput> items,
      SalesInitialReceiptInput initialReceipt,
      Boolean recordOnly) {
    this(
        saleDate,
        salesType,
        partnerId,
        auctionShipmentId,
        paymentStatus,
        salesStatus,
        paymentMethod,
        memo,
        items,
        initialReceipt,
        recordOnly,
        null);
  }

  public SalesSlipCommand(
      LocalDate saleDate,
      SalesType salesType,
      Long partnerId,
      Long auctionShipmentId,
      String paymentStatus,
      String salesStatus,
      String paymentMethod,
      String memo,
      List<SalesSlipItemInput> items,
      SalesInitialReceiptInput initialReceipt) {
    this(
        saleDate,
        salesType,
        partnerId,
        auctionShipmentId,
        paymentStatus,
        salesStatus,
        paymentMethod,
        memo,
        items,
        initialReceipt,
        null);
  }

  public SalesSlipCommand(
      LocalDate saleDate,
      SalesType salesType,
      Long partnerId,
      Long auctionShipmentId,
      String paymentStatus,
      String salesStatus,
      String paymentMethod,
      String memo,
      List<SalesSlipItemInput> items) {
    this(
        saleDate,
        salesType,
        partnerId,
        auctionShipmentId,
        paymentStatus,
        salesStatus,
        paymentMethod,
        memo,
        items,
        null);
  }
}
