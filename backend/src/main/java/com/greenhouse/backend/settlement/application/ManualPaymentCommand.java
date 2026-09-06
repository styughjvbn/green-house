package com.greenhouse.backend.settlement.application;

import java.time.LocalDate;

public record ManualPaymentCommand(
		Long amount,
		LocalDate paymentDate,
		String idempotencyKey,
		String paymentMethod,
		String depositorName,
		String worker,
		String memo) {
}
