package com.greenhouse.backend.work.dto.effect;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DiscardRecordResultRequest(
		@NotNull Long orchidGroupId,
		@NotNull @Min(1) Integer discardQuantity,
		@Size(max = 1000) String reason) {
}
