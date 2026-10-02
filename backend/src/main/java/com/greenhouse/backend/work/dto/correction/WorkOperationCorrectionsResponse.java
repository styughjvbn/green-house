package com.greenhouse.backend.work.dto.correction;

import com.greenhouse.backend.work.application.operation.WorkOperationView;
import com.greenhouse.backend.work.dto.operation.WorkCorrectionDetailResponse;
import java.util.List;

public record WorkOperationCorrectionsResponse(WorkOperationView originalOperation,
		List<WorkCorrectionDetailResponse> corrections) {
}
