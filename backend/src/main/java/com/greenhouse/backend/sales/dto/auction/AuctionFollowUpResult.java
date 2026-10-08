package com.greenhouse.backend.sales.dto.auction;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuctionFollowUpResult(
    AuctionFollowUpResponse followUp, @Schema(nullable = true) AuctionArrivalResponse arrival) {}
