package com.greenhouse.backend.work.operation.application;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.work.api.operation.WorkOperationAction;
import com.greenhouse.backend.work.api.operation.WorkOperationProgress;
import com.greenhouse.backend.work.api.operation.WorkOperationView;
import com.greenhouse.backend.work.api.target.WorkOperationTargetView;
import com.greenhouse.backend.work.api.target.WorkTargetAction;
import com.greenhouse.backend.work.effect.application.WorkEffectJsonCodec;
import com.greenhouse.backend.work.operation.domain.WorkOperation;
import com.greenhouse.backend.work.spi.target.InboundPottingPlanTarget;
import com.greenhouse.backend.work.target.domain.WorkOperationTarget;
import com.greenhouse.backend.work.target.domain.WorkTargetExecution;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Internal Entity and snapshot conversion for public Work values. */
public final class WorkOperationViewFactory {

  private WorkOperationViewFactory() {}

  public static WorkOperationView from(
      WorkOperation operation,
      WorkOperationProgress progress,
      List<WorkOperationTargetView> targets,
      List<WorkOperationAction> availableActions,
      long correctionCount) {
    return new WorkOperationView(
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
        operation.getParentOperation() == null ? null : operation.getParentOperation().getId(),
        operation.getRelationType(),
        TimeConfig.toFarmTime(operation.getVoidedAt()),
        operation.getVoidReason(),
        operation.getVoidMutationId(),
        progress,
        targets,
        availableActions,
        correctionCount);
  }

  public static WorkOperationTargetView from(
      WorkOperationTarget target, WorkTargetExecution execution) {
    return from(target, execution, null, List.of());
  }

  public static WorkOperationTargetView from(
      WorkOperationTarget target,
      WorkTargetExecution execution,
      InboundPottingPlanTarget currentInbound,
      List<WorkTargetAction> availableActions) {
    String varietyName =
        currentInbound == null ? target.getVarietyNameSnapshot() : currentInbound.varietyName();
    int quantity =
        currentInbound == null
            ? target.getQuantitySnapshot()
            : currentInbound.currentQuantity(target.getQuantitySnapshot());
    String potSize =
        currentInbound == null ? target.getPotSizeSnapshot() : currentInbound.potSize();
    Map<String, Object> location =
        currentInbound == null ? target.getLocationSnapshot() : inboundLocation(currentInbound);
    return new WorkOperationTargetView(
        target.getId(),
        target.getTargetReferenceType(),
        target.getOrchidGroupId(),
        target.getInboundRecordId(),
        target.getInclusionSource(),
        varietyName,
        quantity,
        target.getAgeYearSnapshot(),
        target.getPotSizeCodeSnapshot(),
        potSize,
        location,
        execution.getProcessedQuantity(),
        Math.max(0, quantity - execution.getProcessedQuantity()),
        execution.getStatus(),
        TimeConfig.toFarmTime(execution.getStartedAt()),
        TimeConfig.toFarmTime(execution.getCompletedAt()),
        TimeConfig.toFarmTime(execution.getEffectAppliedAt()),
        execution.getWorker(),
        execution.getResultDetails(),
        WorkEffectJsonCodec.targetResultIds(execution.getResultDetails()),
        availableActions);
  }

  private static Map<String, Object> inboundLocation(InboundPottingPlanTarget inbound) {
    Map<String, Object> location = new LinkedHashMap<>();
    location.put("tempLocation", inbound.tempLocation());
    location.put("pottingDueDate", inbound.pottingDueDate());
    return location;
  }
}
