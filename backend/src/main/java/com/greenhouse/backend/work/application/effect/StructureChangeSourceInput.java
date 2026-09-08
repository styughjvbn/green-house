package com.greenhouse.backend.work.application.effect;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

@Schema(name = "StructureChangeSourceRequest")
public record StructureChangeSourceInput(
		@NotNull Long sourceOrchidGroupId,
		@NotNull @Min(1) Integer inputQuantity,
		@DecimalMin(value = "0.0") BigDecimal releasedStartPosition,
		@DecimalMin(value = "0.0", inclusive = false) BigDecimal releasedEndPosition) {
}
