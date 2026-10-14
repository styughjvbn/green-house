package com.greenhouse.backend.work.api.target;

import com.greenhouse.backend.work.spi.target.ResolvedWorkTarget;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(name = "WorkOperationTargetResponse")
public record WorkOperationTargetView(
    Long id,
    WorkTargetReferenceType targetReferenceType,
    Long orchidGroupId,
    Long inboundRecordId,
    WorkTargetInclusionSource inclusionSource,
    String varietyName,
    Integer quantitySnapshot,
    Integer ageYearSnapshot,
    String potSizeCodeSnapshot,
    String potSizeSnapshot,
    Map<String, Object> locationSnapshot,
    Integer processedQuantity,
    Integer remainingQuantity,
    WorkTargetExecutionStatus executionStatus,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    LocalDateTime effectAppliedAt,
    String worker,
    Map<String, Object> resultDetails,
    List<Long> resultOrchidGroupIds,
    List<WorkTargetAction> availableActions) {

  public static WorkOperationTargetView preview(ResolvedWorkTarget target) {
    return new WorkOperationTargetView(
        null,
        WorkTargetReferenceType.ORCHID_GROUP,
        target.orchidGroupId(),
        null,
        null,
        target.varietyName(),
        target.quantity(),
        target.ageYear(),
        target.potSizeCode(),
        target.potSize(),
        target.location(),
        0,
        target.quantity(),
        WorkTargetExecutionStatus.PENDING,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        List.of());
  }
}
