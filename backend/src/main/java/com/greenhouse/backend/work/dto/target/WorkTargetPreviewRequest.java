package com.greenhouse.backend.work.dto.target;

import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record WorkTargetPreviewRequest(
		@NotNull WorkSourceScopeType scopeType,
		Long scopeId,
		String scopeKey,
		List<Long> orchidGroupIds) {
}
