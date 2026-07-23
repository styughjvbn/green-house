package com.greenhouse.backend.work.dto.operation;

import com.greenhouse.backend.work.domain.operation.WorkTypeTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkTypeUpdateRequest(
		@NotBlank @Size(max = 50) String name,
		@NotNull WorkTypeTemplate template,
		boolean active) {
}
