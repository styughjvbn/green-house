package com.greenhouse.backend.work.application.effect;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.greenhouse.backend.work.domain.effect.StructureChangeResultPurpose;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "StructureChangeResultRequest")
public record StructureChangeResultInput(
		@NotNull Long bedZoneId,
		@NotNull @Min(1) Integer quantity,
		Long attributeSourceOrchidGroupId,
		@Size(max = 50) String potSize,
		@Min(0) Integer ageYear,
		@NotNull StructureChangeResultPurpose purpose,
		@Size(max = 50) String placementType,
		@Min(0) Integer trayCount,
		Boolean splitPlacementAllowed,
		@NotNull @DecimalMin(value = "0.0") BigDecimal startPosition,
		@NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal endPosition,
		@Size(max = 1000) String memo) {
}
