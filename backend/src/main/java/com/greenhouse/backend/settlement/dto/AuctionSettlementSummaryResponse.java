package com.greenhouse.backend.settlement.dto;

public record AuctionSettlementSummaryResponse(Long expectedDepositAmount, Long remainingAmount) {
}
