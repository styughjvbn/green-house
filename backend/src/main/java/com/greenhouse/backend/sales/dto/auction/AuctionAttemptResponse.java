package com.greenhouse.backend.sales.dto.auction;

import com.greenhouse.backend.sales.domain.auction.AuctionAttempt;
import com.greenhouse.backend.sales.domain.auction.AuctionAttemptStatus;
import java.time.LocalDate;
import java.util.List;

public record AuctionAttemptResponse(
    Long id,
    LocalDate auctionDate,
    Integer attemptNo,
    AuctionAttemptStatus attemptStatus,
    String failedReason,
    String memo,
    List<AuctionResultLineResponse> resultLines) {
  public static AuctionAttemptResponse from(AuctionAttempt attempt) {
    return new AuctionAttemptResponse(
        attempt.getId(),
        attempt.getAuctionDate(),
        attempt.getAttemptNo(),
        attempt.getAttemptStatus(),
        attempt.getFailedReason(),
        attempt.getMemo(),
        attempt.getResultLines().stream().map(AuctionResultLineResponse::from).toList());
  }
}
