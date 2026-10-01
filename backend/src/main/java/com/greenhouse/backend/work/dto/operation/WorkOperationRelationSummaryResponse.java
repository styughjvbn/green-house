package com.greenhouse.backend.work.dto.operation;

import java.util.List;

public record WorkOperationRelationSummaryResponse(WorkOperationOriginType originType, List<Long> inboundRecordIds,
		int creationBatchSize, boolean hasLinkedOperations, int linkedOperationCount) {
}
