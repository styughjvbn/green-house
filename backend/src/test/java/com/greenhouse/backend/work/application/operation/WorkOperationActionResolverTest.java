package com.greenhouse.backend.work.application.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.work.api.operation.WorkOperationAction;
import com.greenhouse.backend.work.api.operation.WorkOperationRelationType;
import com.greenhouse.backend.work.api.operation.WorkOperationStatus;
import com.greenhouse.backend.work.api.operation.WorkTypeWorkflow;
import com.greenhouse.backend.work.api.target.WorkOperationTargetView;
import com.greenhouse.backend.work.api.target.WorkTargetAction;
import com.greenhouse.backend.work.api.target.WorkTargetExecutionStatus;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.target.WorkTargetExecution;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkOperationActionResolverTest {

  private final WorkOperationActionResolver resolver = new WorkOperationActionResolver();

  @Test
  void exposesOperationActionsFromTheDomainStatusAndTargetProgress() {
    WorkOperation operation = operation(WorkOperationStatus.PLANNED, WorkTypeWorkflow.GENERIC);

    assertThat(resolver.resolveOperation(operation, List.of()))
        .containsExactly(WorkOperationAction.START, WorkOperationAction.CANCEL);

    when(operation.getStatus()).thenReturn(WorkOperationStatus.IN_PROGRESS);
    assertThat(
            resolver.resolveOperation(
                operation, List.of(target(WorkTargetExecutionStatus.PENDING))))
        .containsExactly(WorkOperationAction.PAUSE, WorkOperationAction.CANCEL);
    assertThat(
            resolver.resolveOperation(
                operation,
                List.of(
                    target(WorkTargetExecutionStatus.COMPLETED),
                    target(WorkTargetExecutionStatus.PENDING))))
        .containsExactly(
            WorkOperationAction.PAUSE,
            WorkOperationAction.END_REMAINING,
            WorkOperationAction.CANCEL);
    assertThat(
            resolver.resolveOperation(
                operation, List.of(target(WorkTargetExecutionStatus.COMPLETED))))
        .containsExactly(WorkOperationAction.COMPLETE, WorkOperationAction.CANCEL);

    when(operation.getStatus()).thenReturn(WorkOperationStatus.COMPLETED);
    assertThat(
            resolver.resolveOperation(
                operation, List.of(target(WorkTargetExecutionStatus.COMPLETED))))
        .containsExactly(WorkOperationAction.CANCEL);
  }

  @Test
  void exposesGenericAndDedicatedTargetActionsWithoutFrontendStatusRules() {
    WorkOperation generic = operation(WorkOperationStatus.IN_PROGRESS, WorkTypeWorkflow.GENERIC);
    WorkTargetExecution execution = execution(WorkTargetExecutionStatus.PENDING);

    assertThat(resolver.resolveTarget(generic, execution, 10))
        .containsExactly(WorkTargetAction.START, WorkTargetAction.COMPLETE, WorkTargetAction.SKIP);

    WorkOperation dedicated =
        operation(WorkOperationStatus.IN_PROGRESS, WorkTypeWorkflow.STRUCTURE_CHANGE);
    assertThat(resolver.resolveTarget(dedicated, execution, 10))
        .containsExactly(WorkTargetAction.EXECUTE, WorkTargetAction.SKIP);
    assertThat(resolver.resolveTarget(dedicated, execution, 0))
        .containsExactly(WorkTargetAction.SKIP);

    when(execution.getStatus()).thenReturn(WorkTargetExecutionStatus.COMPLETED);
    assertThat(resolver.resolveTarget(dedicated, execution, 10)).isEmpty();
  }

  @Test
  void exposesCancellationForCompletedMutationWork() {
    WorkOperation operation = operation(WorkOperationStatus.COMPLETED, WorkTypeWorkflow.DISCARD);
    when(operation.getWorkType().supportsMutationVoid()).thenReturn(true);

    when(operation.getWorkType().supportsUserCancellation()).thenReturn(true);
    assertThat(resolver.resolveOperation(operation, List.of()))
        .containsExactly(WorkOperationAction.CANCEL);

    when(operation.getRelationType()).thenReturn(WorkOperationRelationType.MOVEMENT_DISCARD);
    assertThat(resolver.resolveOperation(operation, List.of())).isEmpty();
  }

  private WorkOperation operation(WorkOperationStatus status, WorkTypeWorkflow workflow) {
    WorkType workType = mock(WorkType.class);
    when(workType.workflow()).thenReturn(workflow);
    when(workType.supportsUserCancellation()).thenReturn(workflow == WorkTypeWorkflow.GENERIC);
    WorkOperation operation = mock(WorkOperation.class);
    when(operation.getStatus()).thenReturn(status);
    when(operation.getWorkType()).thenReturn(workType);
    return operation;
  }

  private WorkTargetExecution execution(WorkTargetExecutionStatus status) {
    WorkTargetExecution execution = mock(WorkTargetExecution.class);
    when(execution.getStatus()).thenReturn(status);
    return execution;
  }

  private WorkOperationTargetView target(WorkTargetExecutionStatus status) {
    WorkOperationTargetView target = mock(WorkOperationTargetView.class);
    when(target.executionStatus()).thenReturn(status);
    return target;
  }
}
