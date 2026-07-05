package com.greenhouse.backend.farm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrchidGroupMovePlacementRequest(
	@NotNull Long segmentId,
	@NotNull @Min(1) Integer quantity,
	@Min(1) Integer trayCount
) {
}
