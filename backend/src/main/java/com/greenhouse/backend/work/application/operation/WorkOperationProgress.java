package com.greenhouse.backend.work.application.operation;

import io.swagger.v3.oas.annotations.media.Schema;
import com.greenhouse.backend.work.domain.target.WorkTargetExecutionStatus;
import com.greenhouse.backend.work.application.target.WorkOperationTargetView;
import java.util.List;

@Schema(name = "WorkOperationProgressResponse")
public record WorkOperationProgress(
		int total,
		int pending,
		int inProgress,
		int partial,
		int completed,
		int skipped,
		int canceled,
		int failed,
		int progressPercent) {

	public static WorkOperationProgress empty() {
		return fromCounts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	}

	public static WorkOperationProgress fromCounts(
			int total,
			int pending,
			int inProgress,
			int partial,
			int completed,
			int skipped,
			int canceled,
			int failed,
			int totalQuantity,
			int processedQuantity,
			int skippedQuantity) {
		int percent = totalQuantity == 0 ? 0
				: (int) Math.round((processedQuantity + skippedQuantity) * 100.0 / totalQuantity);
		return new WorkOperationProgress(
				total, pending, inProgress, partial, completed, skipped, canceled, failed, percent);
	}

	public static WorkOperationProgress from(List<WorkOperationTargetView> targets) {
		int total = targets.size();
		int pending = count(targets, WorkTargetExecutionStatus.PENDING);
		int inProgress = count(targets, WorkTargetExecutionStatus.IN_PROGRESS);
		int partial = count(targets, WorkTargetExecutionStatus.PARTIALLY_COMPLETED);
		int completed = count(targets, WorkTargetExecutionStatus.COMPLETED);
		int skipped = count(targets, WorkTargetExecutionStatus.SKIPPED);
		int canceled = count(targets, WorkTargetExecutionStatus.CANCELED);
		int failed = count(targets, WorkTargetExecutionStatus.FAILED);
		int totalQuantity = targets.stream().mapToInt(WorkOperationTargetView::quantitySnapshot).sum();
		int processedQuantity = targets.stream().mapToInt(WorkOperationTargetView::processedQuantity).sum();
		int skippedQuantity = targets.stream()
				.filter(target -> target.executionStatus() == WorkTargetExecutionStatus.SKIPPED)
				.mapToInt(WorkOperationTargetView::remainingQuantity).sum();
		return fromCounts(
				total,
				pending,
				inProgress,
				partial,
				completed,
				skipped,
				canceled,
				failed,
				totalQuantity,
				processedQuantity,
				skippedQuantity);
	}

	private static int count(List<WorkOperationTargetView> targets, WorkTargetExecutionStatus status) {
		return (int) targets.stream().filter(target -> target.executionStatus() == status).count();
	}
}
