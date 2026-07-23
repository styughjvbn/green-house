package com.greenhouse.backend.work.dto.effect;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record StructureChangeSourceRequest(
		@NotNull Long sourceOrchidGroupId,
		@NotNull @Min(1) Integer inputQuantity,
		@DecimalMin(value = "0.0") BigDecimal releasedStartPosition,
		@DecimalMin(value = "0.0", inclusive = false) BigDecimal releasedEndPosition) {
}
