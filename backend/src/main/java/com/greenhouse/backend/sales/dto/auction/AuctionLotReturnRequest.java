package com.greenhouse.backend.sales.dto.auction;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AuctionLotReturnRequest(
    @NotBlank @Size(min = 1, max = 100) String idempotencyKey,
    @Min(1) Integer returnedQuantity,
    @NotNull LocalDate returnDate,
    @Size(max = 100) String worker,
    @Size(max = 1000) String memo) {}
