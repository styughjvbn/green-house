package com.greenhouse.backend.farm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InboundNewVarietyRequest(
	@NotBlank @Size(max = 100) String genus,
	@NotBlank @Size(max = 150) String name,
	@Size(max = 50) String defaultPotSize,
	@Size(max = 1000) String memo
) {
}
