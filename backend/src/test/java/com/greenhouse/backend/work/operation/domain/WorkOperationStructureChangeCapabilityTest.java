package com.greenhouse.backend.work.operation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.work.api.operation.WorkOperationStatus;
import com.greenhouse.backend.work.api.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.api.operation.WorkTypeTemplate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkOperationStructureChangeCapabilityTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 22, 9, 0);

  @Test
  void movementCanBeCorrectedAndVoidedAsAStructureChange() {
    WorkOperation operation =
        completedOperation(WorkTypeDefinition.MOVEMENT, WorkTypeTemplate.MOVEMENT);

    assertThat(operation.isStructureResultCorrectable()).isTrue();
    operation.voidCompletedMutationWork(NOW.plusMinutes(1), "잘못 등록한 이동", "void-movement", 101L);

    assertThat(operation.getStatus()).isEqualTo(WorkOperationStatus.VOIDED);
    assertThat(operation.getVoidMutationId()).isEqualTo(101L);
  }

  @Test
  void discardCannotBeCorrectedButCanBeVoided() {
    WorkOperation operation =
        completedOperation(WorkTypeDefinition.DISCARD, WorkTypeTemplate.DISCARD);

    assertThat(operation.isStructureResultCorrectable()).isFalse();

    operation.voidCompletedMutationWork(NOW.plusMinutes(1), "잘못 등록한 폐기", "void-discard", 102L);

    assertThat(operation.getStatus()).isEqualTo(WorkOperationStatus.VOIDED);
    assertThat(operation.getVoidMutationId()).isEqualTo(102L);
  }

  @Test
  void inProgressStructureChangeCanBeCanceledWithCompensation() {
    WorkType type =
        new WorkType(
            WorkTypeDefinition.REPOT.name(), "분갈이", WorkTypeTemplate.REPOT, true, true, true, 1);
    WorkOperation operation =
        new WorkOperation(
            type,
            "부분 분갈이",
            LocalDate.from(NOW),
            null,
            WorkSourceScopeType.ORCHID_GROUP,
            1L,
            Map.of(),
            Map.of(),
            null,
            null,
            NOW.minusMinutes(1));
    operation.start(NOW);

    operation.voidCompletedMutationWork(NOW.plusMinutes(1), "잘못 등록", "cancel-partial", 103L);

    assertThat(operation.getStatus()).isEqualTo(WorkOperationStatus.VOIDED);
    assertThat(operation.getActualEndAt()).isEqualTo(NOW.plusMinutes(1));
  }

  @Test
  void separatesEndingRemainingWorkFromCancelingARecordedWork() {
    WorkType type =
        new WorkType("PESTICIDE", "농약", WorkTypeTemplate.PESTICIDE, true, false, true, 1);
    WorkOperation stopped =
        new WorkOperation(
            type,
            "기간 작업",
            LocalDate.from(NOW),
            null,
            WorkSourceScopeType.ORCHID_GROUP,
            1L,
            Map.of(),
            Map.of(),
            null,
            null,
            NOW.minusMinutes(1));
    stopped.start(NOW);
    stopped.stop(NOW.plusMinutes(1));

    assertThat(stopped.getStatus()).isEqualTo(WorkOperationStatus.STOPPED);

    WorkOperation canceled =
        new WorkOperation(
            type,
            "기록 작업",
            LocalDate.from(NOW),
            null,
            WorkSourceScopeType.ORCHID_GROUP,
            1L,
            Map.of(),
            Map.of(),
            null,
            null,
            NOW.minusMinutes(1));
    canceled.complete(NOW);
    canceled.cancelRecordedWork(NOW.plusMinutes(1), "잘못 등록", "cancel-record");

    assertThat(canceled.getStatus()).isEqualTo(WorkOperationStatus.CANCELED);
    assertThat(canceled.getVoidReason()).isEqualTo("잘못 등록");
  }

  private WorkOperation completedOperation(
      WorkTypeDefinition definition, WorkTypeTemplate template) {
    WorkType type =
        new WorkType(definition.name(), definition.name(), template, true, false, true, 1);
    WorkOperation operation =
        new WorkOperation(
            type,
            "테스트 작업",
            LocalDate.from(NOW),
            null,
            WorkSourceScopeType.ORCHID_GROUP,
            1L,
            Map.of(),
            Map.of(),
            null,
            null,
            NOW.minusMinutes(1));
    operation.complete(NOW);
    return operation;
  }
}
