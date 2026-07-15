package com.greenhouse.backend.work.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record WorkOperationCorrectionCreateRequest(
		@NotBlank @Size(max = 100) String idempotencyKey,
		@NotBlank @Size(max = 150) String title,
		@NotNull LocalDate workDate,
		@Size(max = 100) String worker,
		@Size(max = 1000) String memo,
		@NotBlank @Size(max = 1000) String reason) {
}
