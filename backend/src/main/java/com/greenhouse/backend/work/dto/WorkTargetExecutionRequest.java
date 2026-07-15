package com.greenhouse.backend.work.dto;

import jakarta.validation.constraints.Size;
import java.util.Map;

public record WorkTargetExecutionRequest(
		@Size(max = 100) String worker,
		Map<String, Object> resultDetails) {
}
