package com.greenhouse.backend.analytics.dto;

import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.domain.operation.WorkTypeTemplate;
import java.time.LocalDate;

public record WorkAnalyticsItemResponse(
		Long id,
		LocalDate workDate,
		String workType,
		WorkTypeTemplate workTypeTemplate,
		String title,
		WorkSourceScopeType sourceScopeType,
		String worker,
		String memo,
		WorkOperationStatus status) {
}
