package com.greenhouse.backend.farm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MergeSourceInputRequest(
		@NotNull Long sourceOrchidGroupId,
		@NotNull @Min(1) Integer inputQuantity) {
}
