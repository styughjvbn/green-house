package com.greenhouse.backend.auction.dto;

import jakarta.validation.constraints.Size;

public record AuctionLotReturnRequest(@Size(max = 100) String worker, @Size(max = 1000) String memo) { }
