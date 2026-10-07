package com.greenhouse.backend.sales.repository.auction;

public interface AuctionTrackingSummaryProjection {

  Number getLotCount();

  Number getShippedQuantity();

  Number getSoldQuantity();

  Number getWaitingQuantity();

  Number getReturnedQuantity();

  Number getReviewRequiredCount();

  Number getTotalAmount();
}
