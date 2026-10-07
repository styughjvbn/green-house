package com.greenhouse.backend.sales.dto.auction.settlement;

import com.greenhouse.backend.sales.application.auction.AuctionDataReader.Result;
import com.greenhouse.backend.sales.domain.auction.settlement.AuctionSettlementLine;
import com.greenhouse.backend.sales.domain.auction.settlement.AuctionSettlementLineStatus;
import java.time.LocalDate;

public record AuctionSettlementLineResponse(
    Long id,
    Long auctionResultLineId,
    Long auctionShipmentLotId,
    LocalDate shipmentDate,
    String varietyName,
    String shipmentGrade,
    Integer quantity,
    Integer unitPrice,
    Long amount,
    AuctionSettlementLineStatus status) {
  public static AuctionSettlementLineResponse from(AuctionSettlementLine line, Result result) {
    return new AuctionSettlementLineResponse(
        line.getId(),
        line.getAuctionResultLineId(),
        line.getAuctionShipmentLotId(),
        result.shipmentDate(),
        result.varietyName(),
        result.shipmentGrade(),
        line.getQuantity(),
        line.getUnitPrice(),
        line.getAmount(),
        line.getStatus());
  }
}
