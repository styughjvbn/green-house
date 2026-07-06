package com.greenhouse.backend.auction.dto;

import com.greenhouse.backend.auction.domain.AuctionInspectionStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AuctionLotResultLineRequest(
	@Size(max = 100) String auctionGrade,
	@NotNull @Min(1) Integer quantity,
	@NotNull @Min(0) Integer unitPrice,
	@Size(max = 1000) String note,
	AuctionInspectionStatus inspectionStatus
) { }
