package com.greenhouse.backend.work.api.operation;

import com.greenhouse.backend.work.api.target.WorkOperationTargetView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(name = "WorkOperationResponse")
public record WorkOperationView(
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
    Long parentOperationId,
    WorkOperationRelationType relationType,
    LocalDateTime voidedAt,
    String voidReason,
    Long voidMutationId,
    WorkOperationProgress progress,
    List<WorkOperationTargetView> targets,
    List<WorkOperationAction> availableActions,
    long correctionCount) {}
