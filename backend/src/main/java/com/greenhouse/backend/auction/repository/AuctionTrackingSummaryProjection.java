package com.greenhouse.backend.auction.repository;

public interface AuctionTrackingSummaryProjection {
	Number getLotCount();
	Number getShippedQuantity();
	Number getSoldQuantity();
	Number getWaitingQuantity();
	Number getReturnedQuantity();
	Number getReviewRequiredCount();
	Number getTotalAmount();
}
