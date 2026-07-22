package com.greenhouse.backend.farm.dto.inbound;

import com.greenhouse.backend.farm.domain.inbound.InboundStatus;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InboundRecordCreateRequest(
		@NotNull LocalDate inboundDate,
		@NotNull InboundType inboundType,
		Long varietyId,
		@Valid InboundNewVarietyRequest newVariety,
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
		Long bedZoneId,
		BigDecimal startPosition,
		BigDecimal endPosition,
		InboundStatus status,
		@Size(max = 50) String worker,
		@Size(max = 1000) String memo) {
}
