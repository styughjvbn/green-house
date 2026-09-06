package com.greenhouse.backend.settlement.dto;

import com.greenhouse.backend.settlement.domain.AuctionSettlement;
import com.greenhouse.backend.settlement.domain.AuctionSettlementStatus;
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
	public static AuctionSettlementListItemResponse from(AuctionSettlement settlement, String auctionHouseName) {
		return new AuctionSettlementListItemResponse(settlement.getId(), settlement.getAuctionHouseId(), auctionHouseName,
				settlement.getAuctionDate(), settlement.getGrossAmount(), settlement.getExpectedDepositAmount(),
				settlement.getRemainingAmount(), settlement.getStatus());
	}
}
