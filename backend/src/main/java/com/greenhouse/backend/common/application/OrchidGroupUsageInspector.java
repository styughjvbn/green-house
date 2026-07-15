package com.greenhouse.backend.common.application;

import java.util.List;
import java.util.Set;

public interface OrchidGroupUsageInspector {

	List<OrchidGroupUsage> inspect(Set<Long> orchidGroupIds, Long sourceWorkOperationId);
}
