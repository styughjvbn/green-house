package com.greenhouse.backend.sales.application.auction;

import com.greenhouse.backend.sales.domain.auction.AuctionFollowUpMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AuctionFollowUpCommand(
    @NotNull AuctionFollowUpMethod method,
    @NotBlank @Size(max = 100) String idempotencyKey,
    @Size(max = 100) String worker,
    @NotBlank @Size(max = 1000) String reason) {}
