package com.greenhouse.backend.auction.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AuctionLotAdjustmentRequest(@NotNull @Min(0) Integer soldQuantity, @NotNull @Min(0) Integer waitingQuantity, @NotNull @Min(0) Integer returnedQuantity, @Size(max = 100) String worker, @Size(max = 1000) String memo) { }
