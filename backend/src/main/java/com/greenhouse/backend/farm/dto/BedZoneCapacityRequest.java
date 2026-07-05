package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.PlacementCapacityMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BedZoneCapacityRequest(
	@NotBlank @Size(max = 100) String placementType,
	@Size(max = 50) String potSize,
	@NotNull PlacementCapacityMode capacityMode,
	@NotNull @Min(0) Integer capacityValue,
	@NotNull Boolean allowed,
	@Size(max = 1000) String memo
) {
}
