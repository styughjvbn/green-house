package com.greenhouse.backend.sales.dto.auction.settlement;

import com.greenhouse.backend.sales.domain.auction.settlement.AuctionSettlement;
import com.greenhouse.backend.sales.domain.auction.settlement.AuctionSettlementStatus;
import java.time.LocalDate;

public record AuctionSettlementListItemResponse(
    Long id,
    Long auctionHouseId,
    String auctionHouseName,
    LocalDate auctionDate,
    Long grossAmount,
    Long expectedDepositAmount,
    Long remainingAmount,
    AuctionSettlementStatus status) {
  public static AuctionSettlementListItemResponse from(
      AuctionSettlement settlement, String auctionHouseName) {
    return new AuctionSettlementListItemResponse(
        settlement.getId(),
        settlement.getAuctionHouseId(),
        auctionHouseName,
        settlement.getAuctionDate(),
        settlement.getGrossAmount(),
        settlement.getExpectedDepositAmount(),
        settlement.getRemainingAmount(),
        settlement.getStatus());
  }
}
