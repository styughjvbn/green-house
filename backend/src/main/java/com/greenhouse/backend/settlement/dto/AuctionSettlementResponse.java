package com.greenhouse.backend.settlement.dto;

import com.greenhouse.backend.settlement.domain.AuctionSettlement;
import com.greenhouse.backend.settlement.domain.AuctionSettlementStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AuctionSettlementResponse(
		Long id,
		Long auctionHouseId,
		String auctionHouseName,
		LocalDate auctionDate,
		LocalDateTime resultReceivedAt,
		LocalDate expectedPaymentDate,
		Long grossAmount,
		Long feeAmount,
		Long deductionAmount,
		Long expectedDepositAmount,
		Long paidAmount,
		Long remainingAmount,
		AuctionSettlementStatus status,
		String memo,
		LocalDateTime confirmedAt,
		String confirmedBy,
		List<AuctionSettlementLineResponse> lines) {
	public static AuctionSettlementResponse from(AuctionSettlement settlement) {
		return new AuctionSettlementResponse(
				settlement.getId(), settlement.getAuctionHouse().getId(), settlement.getAuctionHouse().getName(),
				settlement.getAuctionDate(), settlement.getResultReceivedAt(), settlement.getExpectedPaymentDate(),
				settlement.getGrossAmount(), settlement.getFeeAmount(), settlement.getDeductionAmount(),
				settlement.getExpectedDepositAmount(), settlement.getPaidAmount(), settlement.getRemainingAmount(),
				settlement.getStatus(), settlement.getMemo(), settlement.getConfirmedAt(), settlement.getConfirmedBy(),
				settlement.getLines().stream().map(AuctionSettlementLineResponse::from).toList());
	}
}
