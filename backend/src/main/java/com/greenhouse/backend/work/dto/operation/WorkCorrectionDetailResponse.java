package com.greenhouse.backend.work.dto.operation;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.work.api.correction.WorkQuantityBalanceChange;
import com.greenhouse.backend.work.application.correction.WorkCorrectionResultDetails;
import com.greenhouse.backend.work.domain.correction.WorkOperationCorrection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record WorkCorrectionDetailResponse(
    Long id,
    LocalDateTime createdAt,
    String worker,
    String memo,
    String reason,
    LocalDate beforeWorkDate,
    LocalDate afterWorkDate,
    List<WorkCorrectionAdjustmentResponse> adjustments,
    List<WorkQuantityBalanceChange> quantityBalances) {

  public static WorkCorrectionDetailResponse from(WorkOperationCorrection correction) {
    var result = WorkCorrectionResultDetails.from(correction.getResultDetails());
    return new WorkCorrectionDetailResponse(
        correction.getId(),
        TimeConfig.toFarmTime(correction.getCreatedAt()),
        correction.getWorker(),
        correction.getMemo(),
        correction.getReason(),
        result.beforeWorkDate(),
        result.afterWorkDate(),
        result.adjustments(),
        result.quantityBalances());
  }
}
