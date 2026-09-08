package com.greenhouse.backend.work.dto.correction;

import com.greenhouse.backend.work.application.operation.WorkOperationView;
import java.util.List;

public record WorkOperationCorrectionsResponse(
		WorkOperationView originalOperation,
		List<WorkOperationCorrectionItemResponse> corrections) {
}
