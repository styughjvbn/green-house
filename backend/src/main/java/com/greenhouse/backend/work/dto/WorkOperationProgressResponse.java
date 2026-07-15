package com.greenhouse.backend.work.dto;

import com.greenhouse.backend.work.domain.WorkTargetExecutionStatus;
import java.util.List;

public record WorkOperationProgressResponse(
		int total,
		int pending,
		int inProgress,
		int completed,
		int skipped,
		int canceled,
		int failed,
		int progressPercent) {

	public static WorkOperationProgressResponse from(List<WorkOperationTargetResponse> targets) {
		int total = targets.size();
		int pending = count(targets, WorkTargetExecutionStatus.PENDING);
		int inProgress = count(targets, WorkTargetExecutionStatus.IN_PROGRESS);
		int completed = count(targets, WorkTargetExecutionStatus.COMPLETED);
		int skipped = count(targets, WorkTargetExecutionStatus.SKIPPED);
		int canceled = count(targets, WorkTargetExecutionStatus.CANCELED);
		int failed = count(targets, WorkTargetExecutionStatus.FAILED);
		int processed = completed + skipped;
		int percent = total == 0 ? 0 : (int) Math.round(processed * 100.0 / total);
		return new WorkOperationProgressResponse(
				total, pending, inProgress, completed, skipped, canceled, failed, percent);
	}

	private static int count(List<WorkOperationTargetResponse> targets, WorkTargetExecutionStatus status) {
		return (int) targets.stream().filter(target -> target.executionStatus() == status).count();
	}
}
