package com.greenhouse.backend.farm.dto.collection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrchidGroupCollectionUpdateRequest(
		@NotBlank @Size(max = 100) String name,
		@Size(max = 1000) String description,
		@Size(max = 200) String purpose) {
}
