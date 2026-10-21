package com.greenhouse.backend.sales.api.document;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "SalesSlipItemResponse")
public record SalesSlipDocumentItem(
    Long id,
    Long auctionShipmentLotId,
    String itemName,
    String genus,
    String spec,
    Integer unit,
    Integer boxes,
    Integer quantity,
    Integer unitPrice,
    Integer amount,
    String memo,
    List<SalesSlipDocumentAllocation> allocations,
    @Schema(nullable = true, description = "현재 경매 lot 요약. 출하 전 또는 과거 생성 응답에는 없을 수 있습니다.")
        AuctionLot auctionLot,
    @Schema(nullable = true, description = "품종 식별자. 품목명·속은 작성 당시의 별도 기록이며 현재 마스터명으로 대체하지 않습니다.")
        Long varietyId) {
  @Schema(name = "SalesSlipAuctionLotResponse")
  public record AuctionLot(
      String currentStatus,
      Integer soldQuantity,
      Integer waitingQuantity,
      Integer returnedQuantity) {}
}
