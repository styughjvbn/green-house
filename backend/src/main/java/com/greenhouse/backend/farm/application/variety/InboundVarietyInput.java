package com.greenhouse.backend.farm.application.variety;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@io.swagger.v3.oas.annotations.media.Schema(name = "InboundNewVarietyRequest")
public record InboundVarietyInput(
		@NotBlank @Size(max = 100) String genus,
		@NotBlank @Size(max = 150) String name,
		@Size(max = 50) String defaultPotSize,
		@Size(max = 1000) String memo) {
}
