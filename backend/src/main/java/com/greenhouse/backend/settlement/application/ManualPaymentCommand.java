package com.greenhouse.backend.settlement.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(name = "ManualPaymentRequest")
public record ManualPaymentCommand(
		@NotNull @Positive Long amount,
		@NotNull LocalDate paymentDate,
		@NotBlank @Size(max = 100) String idempotencyKey,
		@Size(max = 30) String paymentMethod,
		@Size(max = 100) String depositorName,
		@Size(max = 100) String worker,
		@Size(max = 1000) String memo) {
}
