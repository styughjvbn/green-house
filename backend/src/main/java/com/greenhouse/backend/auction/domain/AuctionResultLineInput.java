package com.greenhouse.backend.auction.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "AuctionLotResultLineRequest")
public record AuctionResultLineInput(
		@Size(max = 100) String auctionGrade,
		@NotNull @Min(1) Integer quantity,
		@NotNull @Min(0) Integer unitPrice,
		@Size(max = 1000) String note,
		AuctionInspectionStatus inspectionStatus) {
}
