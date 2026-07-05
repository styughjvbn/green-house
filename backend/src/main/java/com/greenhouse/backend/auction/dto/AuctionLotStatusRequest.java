package com.greenhouse.backend.auction.dto;

import com.greenhouse.backend.auction.domain.AuctionLotStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AuctionLotStatusRequest(@NotNull AuctionLotStatus status, @NotBlank @Size(max = 200) String reason, @Size(max = 100) String worker, @Size(max = 1000) String memo) { }
