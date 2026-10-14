package com.greenhouse.backend.work.operation.web.dto;

import java.util.List;
import java.util.Set;

public record WorkOperationBatchCancellationResponse(
    List<Long> canceledWorkOperationIds,
    Long compensationMutationId,
    Set<Long> creationCanceledOrchidGroupIds) {}
