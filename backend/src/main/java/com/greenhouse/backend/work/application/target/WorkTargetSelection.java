package com.greenhouse.backend.work.application.target;

import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import java.util.List;

public record WorkTargetSelection(
		WorkSourceScopeType sourceScopeType,
		Long sourceScopeId,
		String sourceDerivedGroupKey,
		List<Long> sourceOrchidGroupIds) {

	public WorkTargetSelection {
		sourceOrchidGroupIds = sourceOrchidGroupIds == null ? List.of() : List.copyOf(sourceOrchidGroupIds);
	}
}
