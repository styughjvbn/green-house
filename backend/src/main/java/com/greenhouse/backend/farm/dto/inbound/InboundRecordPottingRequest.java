package com.greenhouse.backend.farm.dto.inbound;

import com.greenhouse.backend.farm.dto.transformation.RepotResultOrchidGroupRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record InboundRecordPottingRequest(
		@NotNull LocalDate pottingDate,
		@NotEmpty @Size(max = 100) List<@Valid RepotResultOrchidGroupRequest> results,
		@Size(max = 100) String growthStage,
		@Size(max = 50) String worker,
		@Size(max = 1000) String memo) {
}
