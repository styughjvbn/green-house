package com.greenhouse.backend.farm.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record OrchidGroupMoveRequest(
		@NotNull Long toBedZoneId,
		@DecimalMin(value = "0.0") BigDecimal startPosition,
		@DecimalMin(value = "0.0", inclusive = false) BigDecimal endPosition,
		@Size(max = 100) String worker,
		@Size(max = 1000) String memo) {
}
