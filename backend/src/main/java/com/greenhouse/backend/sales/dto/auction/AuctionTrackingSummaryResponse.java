package com.greenhouse.backend.sales.dto.auction;

public record AuctionTrackingSummaryResponse(
    Integer lotCount,
    Integer shippedQuantity,
    Integer soldQuantity,
    Integer waitingQuantity,
    Integer returnedQuantity,
    Integer reviewRequiredCount,
    Integer totalAmount) {}
