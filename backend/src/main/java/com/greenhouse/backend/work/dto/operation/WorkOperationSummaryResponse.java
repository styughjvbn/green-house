package com.greenhouse.backend.work.dto.operation;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.work.api.operation.WorkOperationAction;
import com.greenhouse.backend.work.api.operation.WorkOperationProgress;
import com.greenhouse.backend.work.api.operation.WorkOperationStatus;
import com.greenhouse.backend.work.api.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.api.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.api.operation.WorkTypeWorkflow;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record WorkOperationSummaryResponse(
    Long id,
    Long workTypeId,
    String workTypeCode,
    String workType,
    WorkTypeTemplate workTypeTemplate,
    WorkTypeWorkflow workTypeWorkflow,
    String title,
    WorkOperationStatus status,
    LocalDate plannedStartDate,
    LocalDate plannedEndDate,
    LocalDateTime actualStartAt,
    LocalDateTime actualEndAt,
    WorkSourceScopeType sourceScopeType,
    Long sourceScopeId,
    Map<String, Object> sourceConditionSnapshot,
    LocalDateTime targetSnapshotAt,
    Map<String, Object> details,
    String worker,
    String memo,
    LocalDateTime voidedAt,
    String voidReason,
    Long voidMutationId,
    WorkOperationRelationSummaryResponse relationSummary,
    WorkOperationProgress progress,
    List<WorkOperationAction> availableActions,
    long correctionCount) {

  public static WorkOperationSummaryResponse from(
      WorkOperation operation,
      WorkOperationRelationSummaryResponse relationSummary,
      WorkOperationProgress progress,
      List<WorkOperationAction> availableActions,
      long correctionCount) {
    return new WorkOperationSummaryResponse(
        operation.getId(),
        operation.getWorkType().getId(),
        operation.getWorkType().getCode(),
        operation.getWorkType().getName(),
        operation.getWorkType().getTemplate(),
        operation.getWorkType().workflow(),
        operation.getTitle(),
        operation.getStatus(),
        operation.getPlannedStartDate(),
        operation.getPlannedEndDate(),
        TimeConfig.toFarmTime(operation.getActualStartAt()),
        TimeConfig.toFarmTime(operation.getActualEndAt()),
        operation.getSourceScopeType(),
        operation.getSourceScopeId(),
        operation.getSourceConditionSnapshot(),
        TimeConfig.toFarmTime(operation.getTargetSnapshotAt()),
        operation.getDetails(),
        operation.getWorker(),
        operation.getMemo(),
        TimeConfig.toFarmTime(operation.getVoidedAt()),
        operation.getVoidReason(),
        operation.getVoidMutationId(),
        relationSummary,
        progress,
        availableActions,
        correctionCount);
  }
}
