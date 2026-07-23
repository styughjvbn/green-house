package com.greenhouse.backend.work.dto.target;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;

public record WorkTargetExecutionRequest(
		@Size(max = 100) String worker,
		Map<String, Object> resultDetails,
		LocalDate completedDate) {
}
