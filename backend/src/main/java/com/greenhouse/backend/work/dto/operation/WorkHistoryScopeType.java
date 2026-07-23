package com.greenhouse.backend.work.dto.operation;

import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;

public enum WorkHistoryScopeType {
	HOUSE,
	PHYSICAL_BED,
	BED_ZONE,
	ORCHID_GROUP;

	public WorkSourceScopeType toSourceScopeType() {
		return WorkSourceScopeType.valueOf(name());
	}
}
