package com.greenhouse.backend.farm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record MultiCreateWorkOperationRequest(
		@NotBlank @Size(max = 100) String idempotencyKey,
		@NotBlank @Size(max = 150) String title,
		@NotNull LocalDate workDate,
		@Size(max = 100) String worker,
		@Size(max = 1000) String memo,
		@NotEmpty @Size(max = 100) List<@Valid MultiCreateOrchidGroupRowRequest> rows) {
}
