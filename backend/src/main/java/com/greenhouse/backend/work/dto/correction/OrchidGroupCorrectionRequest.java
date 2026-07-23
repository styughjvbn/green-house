package com.greenhouse.backend.work.dto.correction;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrchidGroupCorrectionRequest(
		@NotNull Long orchidGroupId,
		@NotNull @Min(0) Integer quantity,
		@NotBlank @Size(max = 50) String status) {
}
