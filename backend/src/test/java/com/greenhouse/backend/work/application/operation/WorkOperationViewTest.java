package com.greenhouse.backend.work.application.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.domain.operation.WorkTypeWorkflow;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkOperationViewTest {

  @Test
  void usesTheStatusAdjustedProgressProvidedByTheAssembler() {
    WorkType type = mock(WorkType.class);
    when(type.getId()).thenReturn(1L);
    when(type.getCode()).thenReturn("REPOT");
    when(type.getName()).thenReturn("분갈이");
    when(type.getTemplate()).thenReturn(WorkTypeTemplate.REPOT);
    when(type.workflow()).thenReturn(WorkTypeWorkflow.STRUCTURE_CHANGE);
    WorkOperation operation = mock(WorkOperation.class);
    when(operation.getWorkType()).thenReturn(type);
    when(operation.getStatus()).thenReturn(WorkOperationStatus.VOIDED);
    var canceledProgress = new WorkOperationProgress(1, 0, 0, 0, 1, 0, 0, 0, 0);

    var response = WorkOperationView.from(operation, canceledProgress, List.of(), List.of(), 0);

    assertThat(response.progress().progressPercent()).isZero();
  }
}
