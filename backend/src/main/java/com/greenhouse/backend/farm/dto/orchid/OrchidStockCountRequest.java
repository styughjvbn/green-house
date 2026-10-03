package com.greenhouse.backend.farm.dto.orchid;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record OrchidStockCountRequest(@NotBlank @Size(max = 100) String idempotencyKey,
		@NotNull @Min(0) Long expectedRevision, @NotNull LocalDate countedDate, @NotNull @Min(0) Integer actualQuantity,
		@NotBlank @Size(max = 1000) String reason, @Size(max = 100) String worker, @Size(max = 1000) String memo) {
	public OrchidStockCountRequest {
		idempotencyKey = trim(idempotencyKey);
		reason = trim(reason);
		worker = trim(worker);
		memo = trim(memo);
	}

	private static String trim(String text) {
		return text == null || text.isBlank() ? null : text.trim();
	}
}
