package com.greenhouse.backend.auction.dto;

import com.greenhouse.backend.auction.domain.AuctionAttempt;
import com.greenhouse.backend.auction.domain.AuctionAttemptStatus;
import java.time.LocalDate;
import java.util.List;

public record AuctionAttemptResponse(Long id, LocalDate auctionDate, Integer attemptNo,
		AuctionAttemptStatus attemptStatus, String failedReason, String memo,
		List<AuctionResultLineResponse> resultLines) {
	public static AuctionAttemptResponse from(AuctionAttempt attempt) {
		return new AuctionAttemptResponse(attempt.getId(), attempt.getAuctionDate(), attempt.getAttemptNo(),
				attempt.getAttemptStatus(), attempt.getFailedReason(), attempt.getMemo(),
				attempt.getResultLines().stream().map(AuctionResultLineResponse::from).toList());
	}
}
