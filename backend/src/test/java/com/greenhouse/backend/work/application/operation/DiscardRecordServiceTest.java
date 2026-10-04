package com.greenhouse.backend.work.application.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import com.greenhouse.backend.work.dto.effect.DiscardRecordCreateRequest;
import com.greenhouse.backend.work.dto.effect.DiscardRecordResultRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationCreateRequest;
import com.greenhouse.backend.work.repository.WorkOperationTargetRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscardRecordServiceTest {

  @Mock WorkOperationPlanService planService;

  @Mock WorkOperationProgressService progressService;

  @Mock WorkTypeService workTypeService;

  @Mock WorkOperationTargetRepository targetRepository;

  @Mock WorkOperationQueryService queryService;

  @Mock WorkOperationSupport support;

  DiscardRecordService service;

  @BeforeEach
  void setUp() {
    service =
        new DiscardRecordService(
            planService, progressService, workTypeService, targetRepository, queryService, support);
  }

  @Test
  void completesEachVarietySpecificDiscardOperationWithOnlyItsTargets() {
    WorkOperationCreateRequest operationRequest = mock(WorkOperationCreateRequest.class);
    WorkOperation firstPlan = operation(10L);
    WorkOperation secondPlan = operation(20L);
    WorkOperationTarget firstTarget = target(101L, 1L, firstPlan);
    WorkOperationTarget secondTarget = target(202L, 2L, secondPlan);
    WorkOperationView firstCompleted = mock(WorkOperationView.class);
    WorkOperationView secondCompleted = mock(WorkOperationView.class);
    when(planService.createBatchOperations(argThat(batch -> batch.operation() == operationRequest)))
        .thenReturn(List.of(firstPlan, secondPlan));
    when(targetRepository.findByWorkOperationIdInAndExcludedAtIsNullOrderByWorkOperationIdAscIdAsc(
            List.of(10L, 20L)))
        .thenReturn(List.of(firstTarget, secondTarget));
    when(queryService.getAll(List.of(10L, 20L)))
        .thenReturn(List.of(firstCompleted, secondCompleted));

    var result =
        service.create(
            new DiscardRecordCreateRequest(
                operationRequest,
                LocalDate.of(2026, 10, 1),
                "작업자",
                List.of(
                    new DiscardRecordResultRequest(1L, 3, " 상태 불량 "),
                    new DiscardRecordResultRequest(2L, 4, " "))));

    assertThat(result).containsExactly(firstCompleted, secondCompleted);
    verify(progressService).startOperation(10L);
    verify(progressService).startOperation(20L);
    verify(progressService)
        .completeTargetForRecord(
            eq(10L),
            eq(101L),
            argThat(
                request ->
                    Integer.valueOf(3).equals(request.resultDetails().get("discardQuantity"))
                        && "상태 불량".equals(request.resultDetails().get("reason"))
                        && "작업자".equals(request.worker())
                        && LocalDate.of(2026, 10, 1).equals(request.completedDate())));
    verify(progressService)
        .completeTargetForRecord(
            eq(20L),
            eq(202L),
            argThat(
                request ->
                    Integer.valueOf(4).equals(request.resultDetails().get("discardQuantity"))
                        && request.resultDetails().get("reason") == null));
  }

  private WorkOperation operation(Long id) {
    WorkOperation operation = mock(WorkOperation.class);
    WorkType workType = mock(WorkType.class);
    when(operation.getId()).thenReturn(id);
    when(operation.getWorkType()).thenReturn(workType);
    when(workType.getCode()).thenReturn("DISCARD");
    return operation;
  }

  private WorkOperationTarget target(Long id, Long orchidGroupId, WorkOperation operation) {
    WorkOperationTarget target = mock(WorkOperationTarget.class);
    when(target.getId()).thenReturn(id);
    when(target.getOrchidGroupId()).thenReturn(orchidGroupId);
    when(target.getWorkOperation()).thenReturn(operation);
    return target;
  }
}
