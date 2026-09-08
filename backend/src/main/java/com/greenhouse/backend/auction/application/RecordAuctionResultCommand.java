package com.greenhouse.backend.auction.application;

import com.greenhouse.backend.auction.domain.AuctionAttemptStatus;
import com.greenhouse.backend.auction.domain.AuctionResultLineInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "AuctionLotResultRequest")
public record RecordAuctionResultCommand(@NotNull LocalDate auctionDate, @Min(1) Integer attemptNo,
		@NotNull AuctionAttemptStatus attemptStatus, @Size(max = 200) String failedReason,
		@Size(max = 1000) String memo, @Valid List<AuctionResultLineInput> resultLines) {
}
