package com.greenhouse.backend.auction.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

public record AuctionLotReturnRequest(@Min(1) Integer returnedQuantity, @Size(max = 100) String worker, @Size(max = 1000) String memo) { }
