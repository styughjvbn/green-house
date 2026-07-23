package com.greenhouse.backend.work.dto.effect;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record InboundPottingPlanBatchCreateRequest(
		@NotNull @Valid InboundPottingPlanCreateRequest plan) {
}
