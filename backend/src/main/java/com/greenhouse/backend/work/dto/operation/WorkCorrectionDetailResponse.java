package com.greenhouse.backend.work.dto.operation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record WorkCorrectionDetailResponse(Long id, Long workOperationId, String title, LocalDate workDate,
		LocalDateTime createdAt, String worker, String reason, List<WorkCorrectionAdjustmentResponse> adjustments) {
}
