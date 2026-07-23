package com.greenhouse.backend.work.dto.correction;

import com.greenhouse.backend.work.dto.operation.WorkOperationResponse;
import java.util.List;

public record WorkOperationCorrectionsResponse(
		WorkOperationResponse originalOperation,
		List<WorkOperationCorrectionItemResponse> corrections) {
}
