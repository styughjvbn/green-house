package com.greenhouse.backend.work.dto.operation;

import java.util.List;

public record WorkOperationDetailResponse(
		WorkOperationDetailSummaryResponse summary,
		List<WorkOperationDetailFieldResponse> fields,
		List<WorkExecutionDetailResponse> executions,
		List<WorkCorrectionDetailResponse> corrections) {
}
