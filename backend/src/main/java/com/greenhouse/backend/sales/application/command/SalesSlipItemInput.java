package com.greenhouse.backend.sales.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(name = "SalesSlipItemRequest")
public record SalesSlipItemInput(
		@NotBlank @Size(max = 150) String itemName,
		@Size(max = 100) String genus,
		@Size(max = 100) String spec,
		@NotNull @Min(1) Integer quantity,
		@NotNull @Min(0) Integer unitPrice,
		@Size(max = 1000) String memo,
		@NotNull List<@Valid SalesSlipAllocationInput> allocations) {
}
