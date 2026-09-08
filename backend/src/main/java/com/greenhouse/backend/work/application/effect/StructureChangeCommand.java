package com.greenhouse.backend.work.application.effect;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "StructureChangeExecutionRequest")
public record StructureChangeCommand(
		@NotBlank @Size(max = 100) String idempotencyKey,
		@NotNull LocalDate completedDate,
		@Size(max = 100) String worker,
		@Size(max = 1000) String memo,
		@NotEmpty @Size(max = 100) List<@Valid StructureChangeSourceInput> sources,
		@NotEmpty @Size(max = 100) List<@Valid StructureChangeResultInput> results) {
}
