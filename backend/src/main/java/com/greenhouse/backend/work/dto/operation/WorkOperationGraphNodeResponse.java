package com.greenhouse.backend.work.dto.operation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record WorkOperationGraphNodeResponse(String id, WorkOperationGraphNodeType nodeType, boolean selected,
		WorkOperationOriginType originType, Long originReferenceId, Integer creationBatchSize, Long workOperationId,
		String workTypeCode, String workType, String title, String status, LocalDate workDate,
		List<Long> orchidGroupIds, List<String> varietyNames, Long mutationId, String mutationType,
		LocalDate effectiveBusinessDate, Instant occurredAt, Long orchidGroupId, Long stateRevision,
		WorkOperationGraphStateResponse state) {
}
