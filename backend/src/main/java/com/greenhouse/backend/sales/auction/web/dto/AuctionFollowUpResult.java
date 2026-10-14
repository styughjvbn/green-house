package com.greenhouse.backend.sales.auction.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuctionFollowUpResult(
    AuctionFollowUpResponse followUp, @Schema(nullable = true) AuctionArrivalResponse arrival) {}
