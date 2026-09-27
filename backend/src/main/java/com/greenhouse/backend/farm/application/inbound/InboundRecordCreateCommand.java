package com.greenhouse.backend.farm.application.inbound;

import com.greenhouse.backend.farm.application.variety.InboundVarietyInput;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@io.swagger.v3.oas.annotations.media.Schema(name = "InboundRecordCreateRequest")
public record InboundRecordCreateCommand(@NotNull LocalDate inboundDate, @NotNull InboundType inboundType,
		Long varietyId, @Valid InboundVarietyInput newVariety, @Min(1) Integer estimatedQuantity,
		@Size(max = 255) String tempLocation, LocalDate pottingDueDate, @Valid InboundPlacementInput placement,
		@Size(max = 50) String worker, @Size(max = 1000) String memo) {
}
