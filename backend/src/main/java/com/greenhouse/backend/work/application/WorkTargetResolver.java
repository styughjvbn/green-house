package com.greenhouse.backend.work.application;

import java.util.List;

public interface WorkTargetResolver {
	List<ResolvedWorkTarget> resolve(WorkTargetSelection selection);

	ResolvedWorkTarget getCurrent(Long orchidGroupId);
}
