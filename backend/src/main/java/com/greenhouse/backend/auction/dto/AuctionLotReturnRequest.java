package com.greenhouse.backend.auction.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record AuctionLotReturnRequest(@Min(1) Integer returnedQuantity, @NotNull LocalDate returnDate, @Size(max = 100) String worker, @Size(max = 1000) String memo) { }
