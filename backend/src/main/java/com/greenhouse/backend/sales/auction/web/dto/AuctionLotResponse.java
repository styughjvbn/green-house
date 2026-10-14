package com.greenhouse.backend.sales.auction.web.dto;

import com.greenhouse.backend.sales.auction.domain.AuctionAttempt;
import com.greenhouse.backend.sales.auction.domain.AuctionAttemptStatus;
import com.greenhouse.backend.sales.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.sales.auction.domain.AuctionLotStatus;
import com.greenhouse.backend.sales.auction.domain.AuctionLotStatusHistory;
import com.greenhouse.backend.sales.auction.domain.AuctionShipmentLot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record AuctionLotResponse(
    Long id,
    LocalDate shipmentDate,
    String auctionMarket,
    String itemName,
    String varietyName,
    String shipmentGrade,
    Integer boxes,
    Integer shippedQuantity,
    Integer soldQuantity,
    Integer waitingQuantity,
    Integer returnedQuantity,
    Integer returnConfirmableQuantity,
    LocalDate returnConfirmedDate,
    AuctionLotStatus currentStatus,
    LocalDate latestAuctionDate,
    Integer failedCount,
    Integer totalAmount,
    AuctionInspectionStatus inspectionStatus,
    String memo,
    List<AuctionAttemptResponse> attempts,
    List<AuctionStatusHistoryResponse> statusHistory,
    @Schema(nullable = true) Boolean quantityAdjustmentAllowed,
    @Schema(nullable = true, description = "현재 유찰 잔량에 대응하는 최신 경매 시도 식별자") Long followUpAttemptId) {

  public static AuctionLotResponse from(AuctionShipmentLot lot, String auctionMarket) {
    return from(lot, auctionMarket, lot.getAttempts(), lot.getStatusHistory());
  }

  public static AuctionLotResponse from(
      AuctionShipmentLot lot,
      String auctionMarket,
      List<AuctionAttempt> attempts,
      List<AuctionLotStatusHistory> statusHistory) {
    var lines = attempts.stream().flatMap(attempt -> attempt.getResultLines().stream()).toList();
    var inspection =
        lines.stream()
            .map(line -> line.getInspectionStatus())
            .max(Comparator.comparingInt(Enum::ordinal))
            .orElse(AuctionInspectionStatus.NORMAL);
    return new AuctionLotResponse(
        lot.getId(),
        lot.getShipment().getShipmentDate(),
        auctionMarket,
        lot.getItemName(),
        lot.getVarietyName(),
        lot.getShipmentGrade(),
        lot.getBoxes(),
        lot.getShippedQuantity(),
        lot.getSoldQuantity(),
        lot.getWaitingQuantity(),
        lot.getReturnedQuantity(),
        lot.getReturnConfirmableQuantity(),
        lot.getReturnConfirmedDate(),
        lot.getCurrentStatus(),
        attempts.stream()
            .map(attempt -> attempt.getAuctionDate())
            .max(LocalDate::compareTo)
            .orElse(null),
        (int)
            attempts.stream()
                .filter(attempt -> attempt.getAttemptStatus().name().contains("FAILED"))
                .count(),
        lines.stream().mapToInt(line -> line.getAmount()).sum(),
        inspection,
        lot.getMemo(),
        attempts.stream().map(AuctionAttemptResponse::from).toList(),
        statusHistory.stream().map(AuctionStatusHistoryResponse::from).toList(),
        lot.isQuantityAdjustmentAllowed(!attempts.isEmpty()),
        attempts.stream()
            .max(Comparator.comparing(AuctionAttempt::getAttemptNo))
            .filter(attempt -> attempt.getAttemptStatus() != AuctionAttemptStatus.RETURN_INFERRED)
            .filter(
                attempt ->
                    attempt.getResultLines().stream()
                        .anyMatch(line -> line.getQuantity() > 0 && line.getAmount() == 0))
            .map(AuctionAttempt::getId)
            .orElse(null));
  }
}
