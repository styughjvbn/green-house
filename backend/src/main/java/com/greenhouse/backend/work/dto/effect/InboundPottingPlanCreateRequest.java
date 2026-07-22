package com.greenhouse.backend.work.dto.effect;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record InboundPottingPlanCreateRequest(
		@NotBlank @Size(max = 150) String title,
		@NotNull LocalDate plannedStartDate,
		LocalDate plannedEndDate,
		@NotEmpty @Size(max = 100) List<@NotNull Long> inboundRecordIds,
		@Size(max = 100) String worker,
		@Size(max = 1000) String memo) {
}
