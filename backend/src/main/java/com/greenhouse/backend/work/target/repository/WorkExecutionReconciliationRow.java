package com.greenhouse.backend.work.target.repository;

import com.greenhouse.backend.work.api.target.WorkTargetExecutionStatus;
import java.time.LocalDateTime;

public record WorkExecutionReconciliationRow(
    Long executionId,
    Long orchidGroupId,
    Integer plannedQuantity,
    Integer processedQuantity,
    WorkTargetExecutionStatus status,
    LocalDateTime effectAppliedAt) {}
