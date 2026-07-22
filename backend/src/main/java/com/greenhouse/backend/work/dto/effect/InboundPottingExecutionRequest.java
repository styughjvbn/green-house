package com.greenhouse.backend.work.dto.effect;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record InboundPottingExecutionRequest(
		@NotNull Long inboundRecordId,
		@NotNull LocalDate pottingDate,
		@NotEmpty @Size(max = 100) List<@Valid InboundPottingResultRequest> results,
		@Size(max = 100) String growthStage,
		@Size(max = 50) String worker,
		@Size(max = 1000) String memo) {
}
