package com.greenhouse.backend.settlement.dto;

import com.greenhouse.backend.settlement.domain.AuctionSettlementLine;
import com.greenhouse.backend.settlement.domain.AuctionSettlementLineStatus;
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
	public static AuctionSettlementLineResponse from(AuctionSettlementLine line) {
		var lot = line.getAuctionShipmentLot();
		return new AuctionSettlementLineResponse(
				line.getId(), line.getAuctionResultLine().getId(), lot.getId(),
				lot.getShipment().getShipmentDate(), lot.getVarietyName(), lot.getShipmentGrade(),
				line.getQuantity(), line.getUnitPrice(), line.getAmount(), line.getStatus());
	}
}
