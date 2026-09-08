package com.greenhouse.backend.sales.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "SalesSlipItemAllocationRequest")
public record SalesSlipAllocationInput(
		@NotNull Long orchidGroupId,
		@NotNull @Min(1) Integer quantity) {
}
