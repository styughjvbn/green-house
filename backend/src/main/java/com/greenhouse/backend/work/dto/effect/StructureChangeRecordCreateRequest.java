package com.greenhouse.backend.work.dto.effect;

import com.greenhouse.backend.work.application.effect.StructureChangeCommand;
import com.greenhouse.backend.work.dto.operation.WorkOperationCreateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record StructureChangeRecordCreateRequest(
		@NotNull @Valid WorkOperationCreateRequest operation,
		@NotNull @Valid StructureChangeCommand execution) {
}
