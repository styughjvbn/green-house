package com.greenhouse.backend.farm.dto.orchid;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record OrchidGroupCreateRequest(
		@NotNull Long bedZoneId,
		@NotNull Long varietyId,
		@NotNull @Min(1) Integer quantity,
		@Size(max = 50) String potSize,
		@Min(0) Integer ageYear,
		@NotBlank @Size(max = 50) String status,
		@Size(max = 50) String placementType,
		@Min(0) Integer trayCount,
		Boolean splitPlacementAllowed,
		@DecimalMin(value = "0.0") BigDecimal startPosition,
		@DecimalMin(value = "0.0", inclusive = false) BigDecimal endPosition,
		@Size(max = 1000) String memo) {
}
