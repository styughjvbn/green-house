package com.greenhouse.backend.farm.application.orchid;

import java.util.List;
import java.util.Set;

/**
 * Farm-owned extension point for references that prevent cancellation or correction. Keep
 * inbound, sales, then work order: command errors use the first blocker's message.
 */
public interface OrchidGroupUsageInspector {

	List<OrchidGroupUsage> inspect(Set<Long> orchidGroupIds, Long sourceWorkOperationId);

}
