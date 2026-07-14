package com.greenhouse.backend.work.dto;

import com.greenhouse.backend.work.domain.WorkSourceScopeType;
import jakarta.validation.constraints.NotNull;

public record WorkTargetPreviewRequest(
		@NotNull WorkSourceScopeType scopeType,
		@NotNull Long scopeId) {
}
