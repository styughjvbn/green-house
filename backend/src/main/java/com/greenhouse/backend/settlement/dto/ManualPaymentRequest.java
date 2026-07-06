package com.greenhouse.backend.settlement.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ManualPaymentRequest(
	@NotNull @Positive Long amount,
	@NotNull LocalDate paymentDate,
	@Size(max = 30) String paymentMethod,
	@Size(max = 100) String depositorName,
	@Size(max = 100) String worker,
	@Size(max = 1000) String memo
) { }
