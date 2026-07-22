package com.greenhouse.backend.farm.dto.structure;

import com.greenhouse.backend.farm.domain.structure.PlacementCapacityMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record BedZoneCapacityRequest(
		@NotBlank @Size(max = 100) String placementType,
		@Size(max = 50) String potSize,
		@NotNull PlacementCapacityMode capacityMode,
		@NotNull @Min(0) Integer capacityValue,
		@NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal unitSpan,
		@NotNull Boolean allowed,
		@Size(max = 1000) String memo) {
}
