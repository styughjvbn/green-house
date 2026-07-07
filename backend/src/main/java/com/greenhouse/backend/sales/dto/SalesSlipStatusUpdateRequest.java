package com.greenhouse.backend.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SalesSlipStatusUpdateRequest(
		@NotBlank @Size(max = 50) String salesStatus,
		@Size(max = 1000) String memo) {
}
