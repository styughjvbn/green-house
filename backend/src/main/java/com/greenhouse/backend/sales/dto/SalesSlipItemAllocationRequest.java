package com.greenhouse.backend.sales.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SalesSlipItemAllocationRequest(
		@NotNull Long orchidGroupId,
		@NotNull @Min(1) Integer quantity) {
}
