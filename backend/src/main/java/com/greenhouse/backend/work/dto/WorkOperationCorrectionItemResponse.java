package com.greenhouse.backend.work.dto;

import com.greenhouse.backend.work.domain.WorkOperationCorrection;
import java.time.LocalDateTime;

public record WorkOperationCorrectionItemResponse(
		Long id,
		String reason,
		LocalDateTime createdAt,
		WorkOperationResponse correctionOperation) {

	public static WorkOperationCorrectionItemResponse from(
			WorkOperationCorrection correction,
			WorkOperationResponse operation) {
		return new WorkOperationCorrectionItemResponse(
				correction.getId(), correction.getReason(), correction.getCreatedAt(), operation);
	}
}
