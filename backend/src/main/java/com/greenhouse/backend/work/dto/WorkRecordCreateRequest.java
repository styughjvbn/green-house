package com.greenhouse.backend.work.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record WorkRecordCreateRequest(
		@NotNull Long workTypeId,
		@NotNull LocalDate workDate,
		@NotBlank @Size(max = 50) String targetType,
		Long targetId,
		@Size(max = 100) String materialName,
		@Size(max = 50) String dilutionRatio,
		@Size(max = 50) String quantity,
		@Size(max = 50) String worker,
		@Size(max = 1000) String memo) {
}
