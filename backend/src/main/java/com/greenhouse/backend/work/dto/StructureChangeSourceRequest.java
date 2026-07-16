package com.greenhouse.backend.work.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StructureChangeSourceRequest(
		@NotNull Long sourceOrchidGroupId,
		@NotNull @Min(1) Integer inputQuantity) {
}
