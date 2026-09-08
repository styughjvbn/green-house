package com.greenhouse.backend.work.dto.effect;

import com.greenhouse.backend.work.application.effect.InboundPottingCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record InboundPottingRecordCreateRequest(@NotNull @Valid InboundPottingPlanCreateRequest plan,
		@NotEmpty @Size(max = 100) List<@Valid InboundPottingCommand> executions) {
}
