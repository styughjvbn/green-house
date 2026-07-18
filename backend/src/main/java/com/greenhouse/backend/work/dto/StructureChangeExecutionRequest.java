package com.greenhouse.backend.work.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record StructureChangeExecutionRequest(
		@NotBlank @Size(max = 100) String idempotencyKey,
		@NotNull LocalDate completedDate,
		@Size(max = 100) String worker,
		@Size(max = 1000) String memo,
		@NotEmpty @Size(max = 100) List<@Valid StructureChangeSourceRequest> sources,
		@NotNull @Min(0) Integer lossQuantity,
		@Size(max = 1000) String lossReason,
		@NotEmpty @Size(max = 100) List<@Valid StructureChangeResultRequest> results) {
}
