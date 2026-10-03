package com.greenhouse.backend.work.dto.operation;

import java.util.List;
import java.util.Set;

public record WorkOperationBatchCancellationResponse(
    List<Long> canceledWorkOperationIds,
    Long compensationMutationId,
    Set<Long> creationCanceledOrchidGroupIds) {}
