package com.greenhouse.backend.work.api.operation;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Work-owned read contracts; storage and query implementation remain internal. */
public interface WorkOperationMetadataApi {

  Map<Long, WorkOperationMetadata> findOriginalsByCorrectionMutationIds(
      Collection<Long> mutationIds);

  List<WorkOperationMetadata> findByIds(Collection<Long> workOperationIds);

  public record WorkOperationMetadata(
      Long id, String workTypeCode, String workType, String title) {}
}
