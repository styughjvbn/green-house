package com.greenhouse.backend.work.application.operation;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.work.api.operation.WorkOperationProgress;
import com.greenhouse.backend.work.api.operation.WorkOperationStatus;
import org.junit.jupiter.api.Test;

class WorkOperationProgressTest {

  @Test
  void resetsCanceledAndVoidedProgressWithoutChangingAuditCounts() {
    var progress = WorkOperationProgress.fromCounts(2, 0, 0, 0, 2, 0, 0, 0, 20, 20, 0);

    var canceled = progress.forStatus(WorkOperationStatus.CANCELED);
    var voided = progress.forStatus(WorkOperationStatus.VOIDED);

    assertThat(canceled.progressPercent()).isZero();
    assertThat(canceled.completed()).isEqualTo(2);
    assertThat(voided.progressPercent()).isZero();
  }

  @Test
  void keepsStoppedProgressBecauseCompletedEffectsRemainApplied() {
    var progress = WorkOperationProgress.fromCounts(2, 0, 0, 0, 1, 0, 1, 0, 20, 10, 0);

    assertThat(progress.forStatus(WorkOperationStatus.STOPPED).progressPercent()).isEqualTo(50);
  }
}
