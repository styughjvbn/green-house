package com.greenhouse.backend.farm.dto.orchid;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record OrchidGroupReconciliationRequest(@NotBlank @Size(max = 100) String idempotencyKey,
		@NotBlank @Size(max = 150) String title, @NotNull LocalDate workDate, @Size(max = 100) String worker,
		@Size(max = 1000) String memo, @NotBlank @Size(max = 1000) String reason,
		@NotNull @Min(0) Integer actualQuantity, @NotBlank @Size(max = 50) String actualStatus,
		@NotNull Long actualBedZoneId, @NotNull BigDecimal actualStartPosition,
		@NotNull BigDecimal actualEndPosition) {
}
