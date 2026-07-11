package com.greenhouse.backend.farm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record InboundRecordUpdateRequest(
		@NotNull LocalDate inboundDate,
		@Min(0) Integer bottleCount,
		@Min(1) Integer estimatedQuantity,
		@Min(1) Integer actualQuantity,
		@Size(max = 255) String tempLocation,
		LocalDate pottingDueDate,
		@Size(max = 50) String potSize,
		@Min(0) Integer ageYear,
		@Size(max = 100) String growthStage,
		@Size(max = 100) String placementType,
		@Min(0) Integer trayCount,
		@Size(max = 50) String worker,
		@Size(max = 1000) String memo) {
}
