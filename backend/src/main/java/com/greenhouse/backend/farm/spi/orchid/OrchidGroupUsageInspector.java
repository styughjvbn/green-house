package com.greenhouse.backend.farm.spi.orchid;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupUsage;
import java.util.List;
import java.util.Set;

/**
 * Farm-owned extension point for references that prevent cancellation or correction. Keep inbound,
 * sales, then work order: command errors use the first blocker's message.
 */
public interface OrchidGroupUsageInspector {

  List<OrchidGroupUsage> inspect(Set<Long> orchidGroupIds, Long sourceWorkOperationId);

  default List<OrchidGroupUsage> inspectExcludingWorkOperations(
      Set<Long> orchidGroupIds, Set<Long> workOperationIds) {
    return inspect(orchidGroupIds, null);
  }

  default List<OrchidGroupUsage> inspect(
      Set<Long> orchidGroupIds, Long sourceWorkOperationId, Set<Long> allowedInboundRecordIds) {
    return inspect(orchidGroupIds, sourceWorkOperationId);
  }
}
