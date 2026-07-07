package com.greenhouse.backend.farm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VarietyUpdateRequest(
		@NotBlank @Size(max = 100) String genus,
		@NotBlank @Size(max = 150) String name,
		@Size(max = 150) String alias,
		@Size(max = 50) String defaultPotSize,
		Boolean saleEnabled,
		@Size(max = 2000) String description,
		@Size(max = 2000) String memo) {
}
