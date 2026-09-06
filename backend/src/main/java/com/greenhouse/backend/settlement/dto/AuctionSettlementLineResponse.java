package com.greenhouse.backend.settlement.dto;

import com.greenhouse.backend.auction.application.AuctionDataReader.Result;
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
	public static AuctionSettlementLineResponse from(AuctionSettlementLine line, Result result) {
		return new AuctionSettlementLineResponse(
				line.getId(), line.getAuctionResultLineId(), line.getAuctionShipmentLotId(),
				result.shipmentDate(), result.varietyName(), result.shipmentGrade(),
				line.getQuantity(), line.getUnitPrice(), line.getAmount(), line.getStatus());
	}
}
