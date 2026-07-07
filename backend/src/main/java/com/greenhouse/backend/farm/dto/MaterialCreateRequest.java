package com.greenhouse.backend.farm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MaterialCreateRequest(
	@NotBlank @Size(max = 50) String category,
	@NotBlank @Size(max = 150) String name,
	@Size(max = 150) String manufacturer,
	@Size(max = 150) String specification,
	@Size(max = 50) String stockQuantity,
	@Size(max = 150) String storageLocation,
	@Size(max = 2000) String usage
) {
}
