package com.greenhouse.backend.work.api.target;

import java.util.Set;

/** Work-owned references used by Farm to guard group changes and cancellation. */
public interface WorkOrchidGroupUsageApi {

  boolean hasUncanceledReference(Long orchidGroupId);

  long countOtherOperations(Set<Long> orchidGroupIds, Long sourceWorkOperationId);

  boolean hasReferencesOutside(Set<Long> orchidGroupIds, Set<Long> workOperationIds);
}
