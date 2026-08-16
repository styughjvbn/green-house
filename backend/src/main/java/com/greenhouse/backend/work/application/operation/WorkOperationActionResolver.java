package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkTypeWorkflow;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import com.greenhouse.backend.work.domain.target.WorkTargetExecutionStatus;
import com.greenhouse.backend.work.dto.operation.WorkOperationAction;
import com.greenhouse.backend.work.dto.operation.WorkOperationProgressResponse;
import com.greenhouse.backend.work.dto.target.WorkOperationTargetResponse;
import com.greenhouse.backend.work.dto.target.WorkTargetAction;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class WorkOperationActionResolver {

	List<WorkOperationAction> resolveOperation(
			WorkOperation operation,
			List<WorkOperationTargetResponse> targets) {
		return resolveOperation(operation, WorkOperationProgressResponse.from(targets));
	}

	List<WorkOperationAction> resolveOperation(
			WorkOperation operation,
			WorkOperationProgressResponse progress) {
		return switch (operation.getStatus()) {
			case PLANNED -> List.of(WorkOperationAction.START, WorkOperationAction.CANCEL);
			case PAUSED -> List.of(WorkOperationAction.RESUME, WorkOperationAction.CANCEL);
			case IN_PROGRESS -> allTargetsClosed(progress)
					? List.of(WorkOperationAction.COMPLETE)
					: List.of(WorkOperationAction.PAUSE, WorkOperationAction.CANCEL);
			case COMPLETED, CANCELED, CORRECTED -> List.of();
		};
	}

	List<WorkTargetAction> resolveTarget(
			WorkOperation operation,
			WorkTargetExecution execution,
			int remainingQuantity) {
		if (operation.getStatus() != WorkOperationStatus.IN_PROGRESS || !isOpen(execution.getStatus())) {
			return List.of();
		}

		List<WorkTargetAction> actions = new ArrayList<>();
		if (remainingQuantity > 0) {
			if (operation.getWorkType().workflow() == WorkTypeWorkflow.GENERIC) {
				if (execution.getStatus() == WorkTargetExecutionStatus.PENDING) {
					actions.add(WorkTargetAction.START);
				}
				actions.add(WorkTargetAction.COMPLETE);
			} else {
				actions.add(WorkTargetAction.EXECUTE);
			}
		}
		actions.add(WorkTargetAction.SKIP);
		return List.copyOf(actions);
	}

	private boolean allTargetsClosed(WorkOperationProgressResponse progress) {
		return progress.total() > 0
				&& progress.pending() == 0
				&& progress.inProgress() == 0
				&& progress.partial() == 0
				&& progress.failed() == 0;
	}

	private boolean isOpen(WorkTargetExecutionStatus status) {
		return status == WorkTargetExecutionStatus.PENDING
				|| status == WorkTargetExecutionStatus.IN_PROGRESS
				|| status == WorkTargetExecutionStatus.PARTIALLY_COMPLETED;
	}
}
