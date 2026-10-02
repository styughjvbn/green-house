package com.greenhouse.backend.work.dto.operation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkOperationCancellationRequest(@NotBlank @Size(max = 100) String idempotencyKey,
		@NotBlank @Size(max = 1000) String reason) {
}
