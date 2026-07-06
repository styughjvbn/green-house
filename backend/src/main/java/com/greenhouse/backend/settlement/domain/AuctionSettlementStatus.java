package com.greenhouse.backend.settlement.domain;

public enum AuctionSettlementStatus {
	CREATED,
	PAYMENT_WAITING,
	PARTIALLY_PAID,
	PAID,
	AMOUNT_MISMATCH,
	REVIEW_REQUIRED,
	CANCELLED
}
