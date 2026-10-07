package com.greenhouse.backend.sales.dto.auction;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.sales.domain.auction.AuctionLotStatus;
import com.greenhouse.backend.sales.domain.auction.AuctionLotStatusHistory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record AuctionStatusHistoryResponse(
    Long id,
    AuctionLotStatus previousStatus,
    AuctionLotStatus newStatus,
    LocalDateTime changedAt,
    String reason,
    String worker,
    String memo,
    @Schema(nullable = true) Integer previousSoldQuantity,
    @Schema(nullable = true) Integer newSoldQuantity,
    @Schema(nullable = true) Integer previousWaitingQuantity,
    @Schema(nullable = true) Integer newWaitingQuantity,
    @Schema(nullable = true) Integer previousReturnedQuantity,
    @Schema(nullable = true) Integer newReturnedQuantity) {
  public static AuctionStatusHistoryResponse from(AuctionLotStatusHistory history) {
    return new AuctionStatusHistoryResponse(
        history.getId(),
        history.getPreviousStatus(),
        history.getNewStatus(),
        TimeConfig.toFarmTime(history.getChangedAt()),
        history.getReason(),
        history.getWorker(),
        history.getMemo(),
        history.getPreviousSoldQuantity(),
        history.getNewSoldQuantity(),
        history.getPreviousWaitingQuantity(),
        history.getNewWaitingQuantity(),
        history.getPreviousReturnedQuantity(),
        history.getNewReturnedQuantity());
  }
}
