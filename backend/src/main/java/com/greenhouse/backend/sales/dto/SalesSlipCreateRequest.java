package com.greenhouse.backend.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record SalesSlipCreateRequest(
	@NotNull LocalDate saleDate,
	@NotNull Long customerId,
	@Size(max = 50) String paymentStatus,
	@Size(max = 50) String salesStatus,
	@Size(max = 50) String paymentMethod,
	@Size(max = 1000) String memo,
	@NotEmpty List<@Valid SalesSlipItemRequest> items
) {
}
