package com.greenhouse.backend.work.dto.operation;

public record WorkCorrectionAdjustmentResponse(
		Long orchidGroupId,
		Integer beforeQuantity,
		Integer afterQuantity,
		String beforeStatus,
		String afterStatus) {
}
