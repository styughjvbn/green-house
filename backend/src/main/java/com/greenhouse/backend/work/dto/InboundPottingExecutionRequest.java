package com.greenhouse.backend.work.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InboundPottingExecutionRequest(
		@NotNull Long inboundRecordId,
		@NotNull LocalDate pottingDate,
		@NotNull @Min(1) Integer actualQuantity,
		@Size(max = 50) String potSize,
		@Min(0) Integer ageYear,
		@Size(max = 100) String growthStage,
		@Size(max = 100) String placementType,
		@Min(0) Integer trayCount,
		@NotNull Long bedZoneId,
		BigDecimal startPosition,
		BigDecimal endPosition,
		@Size(max = 50) String worker,
		@Size(max = 1000) String memo) {
}
