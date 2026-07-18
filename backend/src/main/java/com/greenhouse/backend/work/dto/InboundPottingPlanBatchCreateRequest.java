package com.greenhouse.backend.work.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record InboundPottingPlanBatchCreateRequest(
		@NotNull @Valid InboundPottingPlanCreateRequest plan) {
}
