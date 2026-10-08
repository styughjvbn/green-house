package com.greenhouse.backend.work.api.correction;

import java.util.List;
import java.util.Set;

/** Work-owned references used by Farm when executing or correcting structure changes. */
public interface StructureChangeReferenceApi {

  Set<Long> getActiveOrchidGroupIds(Long workOperationId);

  List<Long> getCorrectableResultOrchidGroupIds(Long operationId);

  StructureChangeMutationReferences getMutationReferences(
      Long operationId, Set<Long> orchidGroupIds);
}
