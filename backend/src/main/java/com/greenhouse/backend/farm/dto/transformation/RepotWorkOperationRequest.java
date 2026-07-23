package com.greenhouse.backend.farm.dto.transformation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record RepotWorkOperationRequest(
		@NotBlank @Size(max = 100) String idempotencyKey,
		@NotBlank @Size(max = 150) String title,
		@NotNull LocalDate workDate,
		@Size(max = 100) String worker,
		@Size(max = 1000) String memo,
		@NotNull Long sourceOrchidGroupId,
		@NotNull @Min(1) Integer inputQuantity,
		@NotNull @Min(0) Integer lossQuantity,
		@Size(max = 1000) String lossReason,
		@NotEmpty @Size(max = 100) List<@Valid RepotResultOrchidGroupRequest> results,
		@Size(max = 20) Set<@NotNull Long> inheritCollectionIds) {
}
