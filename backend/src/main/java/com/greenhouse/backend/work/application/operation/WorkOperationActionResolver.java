package com.greenhouse.backend.work.application.operation;

import com.greenhouse.backend.work.application.operation.WorkOperationProgress;
import com.greenhouse.backend.work.application.target.WorkOperationTargetView;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationAction;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkTypeWorkflow;
import com.greenhouse.backend.work.domain.target.WorkTargetAction;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import com.greenhouse.backend.work.domain.target.WorkTargetExecutionStatus;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class WorkOperationActionResolver {

	List<WorkOperationAction> resolveOperation(WorkOperation operation, List<WorkOperationTargetView> targets) {
		return resolveOperation(operation, WorkOperationProgress.from(targets));
	}

	List<WorkOperationAction> resolveOperation(WorkOperation operation, WorkOperationProgress progress) {
		return switch (operation.getStatus()) {
			case PLANNED -> operation.getWorkType().supportsUserCancellation()
					? List.of(WorkOperationAction.START, WorkOperationAction.CANCEL)
					: List.of(WorkOperationAction.START);
			case PAUSED -> activeActions(operation, progress, WorkOperationAction.RESUME);
			case IN_PROGRESS ->
				allTargetsClosed(progress) ? List.of(WorkOperationAction.COMPLETE, WorkOperationAction.CANCEL)
						: activeActions(operation, progress, WorkOperationAction.PAUSE);
			case COMPLETED -> completedActions(operation);
			case STOPPED, CANCELED, VOIDED -> List.of();
		};
	}

	private List<WorkOperationAction> completedActions(WorkOperation operation) {
		var actions = new ArrayList<WorkOperationAction>();
		if (operation.getRelationType() == null && operation.getWorkType().supportsUserCancellation())
			actions.add(WorkOperationAction.CANCEL);
		if (operation.isStructureResultCorrectable())
			actions.add(WorkOperationAction.CORRECT);
		return List.copyOf(actions);
	}

	private List<WorkOperationAction> activeActions(WorkOperation operation, WorkOperationProgress progress,
			WorkOperationAction lifecycleAction) {
		if (!operation.getWorkType().supportsUserCancellation()) {
			return List.of(lifecycleAction);
		}
		if (progress.completed() > 0 || progress.partial() > 0) {
			return List.of(lifecycleAction, WorkOperationAction.END_REMAINING, WorkOperationAction.CANCEL);
		}
		return List.of(lifecycleAction, WorkOperationAction.CANCEL);
	}

	List<WorkTargetAction> resolveTarget(WorkOperation operation, WorkTargetExecution execution,
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
			}
			else {
				actions.add(WorkTargetAction.EXECUTE);
			}
		}
		actions.add(WorkTargetAction.SKIP);
		return List.copyOf(actions);
	}

	private boolean allTargetsClosed(WorkOperationProgress progress) {
		return progress.total() > 0 && progress.pending() == 0 && progress.inProgress() == 0 && progress.partial() == 0
				&& progress.failed() == 0;
	}

	private boolean isOpen(WorkTargetExecutionStatus status) {
		return status == WorkTargetExecutionStatus.PENDING || status == WorkTargetExecutionStatus.IN_PROGRESS
				|| status == WorkTargetExecutionStatus.PARTIALLY_COMPLETED;
	}

}
