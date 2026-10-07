package com.greenhouse.backend.sales.domain.auction.settlement;

public enum AuctionSettlementStatus {
  CREATED,
  PAYMENT_WAITING,
  PARTIALLY_PAID,
  PAID,
  AMOUNT_MISMATCH,
  REVIEW_REQUIRED,
  CANCELLED
}
