package com.greenhouse.backend.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import com.greenhouse.backend.sales.domain.SalesType;

public record SalesSlipCreateRequest(
	@NotNull LocalDate saleDate,
	SalesType salesType,
	Long partnerId,
	Long auctionShipmentId,
	@Size(max = 50) String paymentStatus,
	@Size(max = 50) String salesStatus,
	@Size(max = 50) String paymentMethod,
	@Size(max = 1000) String memo,
	@NotNull List<@Valid SalesSlipItemRequest> items
) {
}
