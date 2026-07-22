package com.greenhouse.backend.farm.dto.transformation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MergeWorkOperationRequest(
		@NotEmpty @Size(min = 2, max = 100) List<@Valid MergeSourceInputRequest> sources,
		@NotNull @Min(0) Integer lossQuantity,
		@Size(max = 1000) String lossReason,
		@NotNull @Valid RepotResultOrchidGroupRequest result) {
}
