package com.greenhouse.backend.work.dto.correction;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.work.domain.correction.WorkOperationCorrection;
import com.greenhouse.backend.work.dto.operation.WorkOperationResponse;
import java.time.LocalDateTime;
import java.util.Map;

public record WorkOperationCorrectionItemResponse(
		Long id,
		String reason,
		LocalDateTime createdAt,
		WorkOperationResponse correctionOperation,
		Map<String, Object> effectDetails) {

	public static WorkOperationCorrectionItemResponse from(
			WorkOperationCorrection correction,
			WorkOperationResponse operation,
			Map<String, Object> effectDetails) {
		return new WorkOperationCorrectionItemResponse(
				correction.getId(), correction.getReason(), TimeConfig.toFarmTime(correction.getCreatedAt()), operation,
				effectDetails);
	}
}
