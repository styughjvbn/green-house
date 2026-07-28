package com.greenhouse.backend.work.dto.effect;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record StructureChangeRecordBatchCreateRequest(
		@NotEmpty @Size(max = 100) List<@Valid StructureChangeRecordCreateRequest> records) {
}
