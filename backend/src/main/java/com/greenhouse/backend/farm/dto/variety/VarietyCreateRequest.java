package com.greenhouse.backend.farm.dto.variety;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VarietyCreateRequest(
		@NotBlank @Size(max = 100) String genus,
		@NotBlank @Size(max = 150) String name,
		@Size(max = 150) String alias,
		@Size(max = 50) String defaultPotSize,
		@Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "색상은 #RRGGBB 형식이어야 합니다.") String color,
		Boolean saleEnabled,
		@Size(max = 2000) String description,
		@Size(max = 2000) String memo) {
}
