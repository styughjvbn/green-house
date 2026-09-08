package com.greenhouse.backend.work.application.correction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "OrchidGroupCorrectionRequest")
public record OrchidGroupCorrectionInput(
		@NotNull Long orchidGroupId,
		@NotNull @Min(0) Integer quantity,
		@NotBlank @Size(max = 50) String status) {
}
