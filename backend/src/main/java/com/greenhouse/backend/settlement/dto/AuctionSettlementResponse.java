package com.greenhouse.backend.settlement.dto;

import com.greenhouse.backend.auction.application.AuctionDataReader.Result;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.settlement.domain.AuctionSettlement;
import com.greenhouse.backend.settlement.domain.AuctionSettlementStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AuctionSettlementResponse(Long id, Long auctionHouseId, String auctionHouseName, LocalDate auctionDate,
		LocalDateTime resultReceivedAt, LocalDate expectedPaymentDate, Long grossAmount, Long feeAmount,
		Long deductionAmount, Long expectedDepositAmount, Long paidAmount, Long remainingAmount,
		AuctionSettlementStatus status, String memo, LocalDateTime confirmedAt, String confirmedBy,
		List<AuctionSettlementLineResponse> lines) {
	public static AuctionSettlementResponse from(AuctionSettlement settlement, String auctionHouseName,
			Map<Long, Result> results) {
		return new AuctionSettlementResponse(settlement.getId(), settlement.getAuctionHouseId(), auctionHouseName,
				settlement.getAuctionDate(), TimeConfig.toFarmTime(settlement.getResultReceivedAt()),
				settlement.getExpectedPaymentDate(), settlement.getGrossAmount(), settlement.getFeeAmount(),
				settlement.getDeductionAmount(), settlement.getExpectedDepositAmount(), settlement.getPaidAmount(),
				settlement.getRemainingAmount(), settlement.getStatus(), settlement.getMemo(),
				TimeConfig.toFarmTime(settlement.getConfirmedAt()), settlement.getConfirmedBy(),
				settlement.getLines()
					.stream()
					.map(line -> AuctionSettlementLineResponse.from(line, results.get(line.getAuctionResultLineId())))
					.toList());
	}
}
