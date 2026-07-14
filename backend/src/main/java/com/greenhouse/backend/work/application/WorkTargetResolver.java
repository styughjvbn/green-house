package com.greenhouse.backend.work.application;

import com.greenhouse.backend.work.domain.WorkSourceScopeType;
import java.util.List;

public interface WorkTargetResolver {
	List<ResolvedWorkTarget> resolve(WorkSourceScopeType scopeType, Long scopeId);

	ResolvedWorkTarget getCurrent(Long orchidGroupId);
}
