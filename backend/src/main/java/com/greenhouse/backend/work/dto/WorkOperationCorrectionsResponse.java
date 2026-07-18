package com.greenhouse.backend.work.dto;

import java.util.List;

public record WorkOperationCorrectionsResponse(
		WorkOperationResponse originalOperation,
		List<WorkOperationCorrectionItemResponse> corrections) {
}
