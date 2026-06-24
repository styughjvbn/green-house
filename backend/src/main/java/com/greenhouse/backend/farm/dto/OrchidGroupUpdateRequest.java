package com.greenhouse.backend.farm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrchidGroupUpdateRequest(
	@Size(max = 100) String genus,
	@NotBlank @Size(max = 150) String varietyName,
	@NotNull @Min(1) Integer quantity,
	@Size(max = 50) String potSize,
	@Min(0) Integer ageYear,
	@NotBlank @Size(max = 50) String status,
	@Size(max = 50) String placementType,
	@Min(0) Integer trayCount,
	@Size(max = 1000) String memo
) {
}
