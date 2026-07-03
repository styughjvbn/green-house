package com.greenhouse.backend.auction.dto;

public record AuctionTrackingSummaryResponse(Integer lotCount, Integer shippedQuantity, Integer soldQuantity, Integer waitingQuantity, Integer returnedQuantity, Integer reviewRequiredCount, Integer totalAmount) { }
