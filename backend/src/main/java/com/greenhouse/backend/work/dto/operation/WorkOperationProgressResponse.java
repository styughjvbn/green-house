package com.greenhouse.backend.work.dto.operation;

import com.greenhouse.backend.work.domain.target.WorkTargetExecutionStatus;
import com.greenhouse.backend.work.dto.target.WorkOperationTargetResponse;
import java.util.List;

public record WorkOperationProgressResponse(
		int total,
		int pending,
		int inProgress,
		int partial,
		int completed,
		int skipped,
		int canceled,
		int failed,
		int progressPercent) {

	public static WorkOperationProgressResponse from(List<WorkOperationTargetResponse> targets) {
		int total = targets.size();
		int pending = count(targets, WorkTargetExecutionStatus.PENDING);
		int inProgress = count(targets, WorkTargetExecutionStatus.IN_PROGRESS);
		int partial = count(targets, WorkTargetExecutionStatus.PARTIALLY_COMPLETED);
		int completed = count(targets, WorkTargetExecutionStatus.COMPLETED);
		int skipped = count(targets, WorkTargetExecutionStatus.SKIPPED);
		int canceled = count(targets, WorkTargetExecutionStatus.CANCELED);
		int failed = count(targets, WorkTargetExecutionStatus.FAILED);
		int totalQuantity = targets.stream().mapToInt(WorkOperationTargetResponse::quantitySnapshot).sum();
		int processedQuantity = targets.stream().mapToInt(WorkOperationTargetResponse::processedQuantity).sum();
		int skippedQuantity = targets.stream()
				.filter(target -> target.executionStatus() == WorkTargetExecutionStatus.SKIPPED)
				.mapToInt(WorkOperationTargetResponse::remainingQuantity).sum();
		int percent = totalQuantity == 0 ? 0
				: (int) Math.round((processedQuantity + skippedQuantity) * 100.0 / totalQuantity);
		return new WorkOperationProgressResponse(
				total, pending, inProgress, partial, completed, skipped, canceled, failed, percent);
	}

	private static int count(List<WorkOperationTargetResponse> targets, WorkTargetExecutionStatus status) {
		return (int) targets.stream().filter(target -> target.executionStatus() == status).count();
	}
}
