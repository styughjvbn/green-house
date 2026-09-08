package com.greenhouse.backend.sales.application.command;

import com.greenhouse.backend.sales.domain.SalesType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "SalesSlipCreateRequest")
public record SalesSlipCommand(
	@NotNull LocalDate saleDate,
	SalesType salesType,
	Long partnerId,
	Long auctionShipmentId,
	@Size(max = 50) String paymentStatus,
	@Size(max = 50) String salesStatus,
	@Size(max = 50) String paymentMethod,
	@Size(max = 1000) String memo,
	@NotNull List<@Valid SalesSlipItemInput> items
) {
}
