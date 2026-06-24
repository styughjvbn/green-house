package com.greenhouse.backend.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerCreateRequest(
	@NotBlank @Size(max = 150) String name,
	@Size(max = 100) String ownerName,
	@Size(max = 50) String phone,
	@Size(max = 1000) String address,
	@Size(max = 1000) String memo
) {
}
