package com.greenhouse.backend.work.application;

import com.greenhouse.backend.work.domain.WorkSourceScopeType;
import java.util.List;

public record WorkTargetSelection(
		WorkSourceScopeType scopeType,
		Long scopeId,
		String scopeKey,
		List<Long> orchidGroupIds) {

	public WorkTargetSelection {
		orchidGroupIds = orchidGroupIds == null ? List.of() : List.copyOf(orchidGroupIds);
	}
}
