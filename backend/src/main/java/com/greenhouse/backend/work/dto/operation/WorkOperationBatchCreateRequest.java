package com.greenhouse.backend.work.dto.operation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record WorkOperationBatchCreateRequest(
		@NotNull @Valid WorkOperationCreateRequest operation) {
}
