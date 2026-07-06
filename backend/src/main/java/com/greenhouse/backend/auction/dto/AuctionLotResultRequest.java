package com.greenhouse.backend.auction.dto;

import com.greenhouse.backend.auction.domain.AuctionAttemptStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record AuctionLotResultRequest(
	@NotNull LocalDate auctionDate,
	@Min(1) Integer attemptNo,
	@NotNull AuctionAttemptStatus attemptStatus,
	@Size(max = 200) String failedReason,
	@Size(max = 1000) String memo,
	@Valid List<AuctionLotResultLineRequest> resultLines
) { }
