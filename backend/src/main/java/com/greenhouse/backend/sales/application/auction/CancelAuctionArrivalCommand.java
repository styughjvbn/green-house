package com.greenhouse.backend.sales.application.auction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CancelAuctionArrivalCommand(
    @NotNull LocalDate correctionDate,
    @NotBlank @Size(max = 100) String idempotencyKey,
    @Size(max = 100) String worker,
    @NotBlank @Size(max = 1000) String reason) {}
