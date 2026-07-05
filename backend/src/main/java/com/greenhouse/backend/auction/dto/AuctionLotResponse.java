package com.greenhouse.backend.auction.dto;

import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import com.greenhouse.backend.auction.domain.AuctionLotStatus;
import com.greenhouse.backend.auction.domain.AuctionShipmentLot;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record AuctionLotResponse(Long id, LocalDate shipmentDate, String auctionMarket, String itemName, String varietyName, String shipmentGrade, Integer boxes, Integer shippedQuantity, Integer soldQuantity, Integer waitingQuantity, Integer returnedQuantity, Integer returnConfirmableQuantity, AuctionLotStatus currentStatus, LocalDate latestAuctionDate, Integer failedCount, Integer totalAmount, AuctionInspectionStatus inspectionStatus, String memo, List<AuctionAttemptResponse> attempts, List<AuctionStatusHistoryResponse> statusHistory) {
	public static AuctionLotResponse from(AuctionShipmentLot lot) {
		var lines = lot.getAttempts().stream().flatMap(attempt -> attempt.getResultLines().stream()).toList();
		var inspection = lines.stream().map(line -> line.getInspectionStatus()).max(Comparator.comparingInt(Enum::ordinal)).orElse(AuctionInspectionStatus.NORMAL);
		return new AuctionLotResponse(lot.getId(), lot.getShipment().getShipmentDate(), lot.getShipment().getAuctionMarket(), lot.getItemName(), lot.getVarietyName(), lot.getShipmentGrade(), lot.getBoxes(), lot.getShippedQuantity(), lot.getSoldQuantity(), lot.getWaitingQuantity(), lot.getReturnedQuantity(), lot.getReturnConfirmableQuantity(), lot.getCurrentStatus(), lot.getAttempts().stream().map(attempt -> attempt.getAuctionDate()).max(LocalDate::compareTo).orElse(null), (int) lot.getAttempts().stream().filter(attempt -> attempt.getAttemptStatus().name().contains("FAILED")).count(), lines.stream().mapToInt(line -> line.getAmount()).sum(), inspection, lot.getMemo(), lot.getAttempts().stream().map(AuctionAttemptResponse::from).toList(), lot.getStatusHistory().stream().map(AuctionStatusHistoryResponse::from).toList());
	}
}
