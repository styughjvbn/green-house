package com.greenhouse.backend.work.dto.operation;

import java.time.LocalDateTime;
import java.util.List;

public record WorkExecutionDetailResponse(
		Long id,
		String executionKey,
		String resultType,
		LocalDateTime appliedAt,
		LocalDateTime canceledAt,
		String worker,
		Long targetId,
		Long inboundRecordId,
		List<WorkExecutionSourceResponse> sources,
		List<WorkExecutionResultResponse> results,
		Integer lossQuantity,
		Integer actualQuantity,
		String reason,
		Long linkedWorkOperationId) {
}
