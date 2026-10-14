package com.greenhouse.backend.work.operation.web.dto;

import java.util.List;

public record WorkOperationRelationSummaryResponse(
    WorkOperationOriginType originType,
    List<Long> inboundRecordIds,
    int creationBatchSize,
    boolean hasLinkedOperations,
    int linkedOperationCount) {}
