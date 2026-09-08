package com.greenhouse.backend.work.dto.operation;

import com.greenhouse.backend.work.application.target.WorkTargetSelection;
import com.greenhouse.backend.work.application.target.WorkTargetSelectionInput;
import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record WorkOperationCreateRequest(@NotNull Long workTypeId, @NotBlank @Size(max = 150) String title,
		@NotNull LocalDate plannedStartDate, LocalDate plannedEndDate, @NotNull WorkSourceScopeType sourceScopeType,
		Long sourceScopeId, String sourceDerivedGroupKey, List<Long> sourceOrchidGroupIds, Map<String, Object> details,
		@Size(max = 100) String worker, @Size(max = 1000) String memo,
		List<Long> excludedOrchidGroupIds) implements WorkTargetSelectionInput {

	public WorkOperationCreateRequest(Long workTypeId, String title, LocalDate plannedStartDate,
			LocalDate plannedEndDate, WorkTargetSelection targetSelection, Map<String, Object> details, String worker,
			String memo, List<Long> excludedOrchidGroupIds) {
		this(workTypeId, title, plannedStartDate, plannedEndDate, targetSelection.sourceScopeType(),
				targetSelection.sourceScopeId(), targetSelection.sourceDerivedGroupKey(),
				targetSelection.sourceOrchidGroupIds(), details, worker, memo, excludedOrchidGroupIds);
	}
}
