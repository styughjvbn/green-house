package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkTypeWorkflow;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import com.greenhouse.backend.work.domain.target.WorkTargetExecutionStatus;
import com.greenhouse.backend.work.dto.operation.WorkOperationAction;
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
		return switch (operation.getStatus()) {
			case PLANNED -> List.of(WorkOperationAction.START, WorkOperationAction.CANCEL);
			case PAUSED -> List.of(WorkOperationAction.RESUME, WorkOperationAction.CANCEL);
			case IN_PROGRESS -> allTargetsClosed(targets)
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

	private boolean allTargetsClosed(List<WorkOperationTargetResponse> targets) {
		return !targets.isEmpty() && targets.stream().allMatch(target -> switch (target.executionStatus()) {
			case COMPLETED, SKIPPED, CANCELED -> true;
			case PENDING, IN_PROGRESS, PARTIALLY_COMPLETED, FAILED -> false;
		});
	}

	private boolean isOpen(WorkTargetExecutionStatus status) {
		return status == WorkTargetExecutionStatus.PENDING
				|| status == WorkTargetExecutionStatus.IN_PROGRESS
				|| status == WorkTargetExecutionStatus.PARTIALLY_COMPLETED;
	}
}
