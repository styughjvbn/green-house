package com.greenhouse.backend.sales.direct.web.dto;

import com.greenhouse.backend.sales.direct.domain.DirectReturnArrival;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;

public record DirectReturnArrivalResponse(
    Long id,
    Long returnChangeId,
    Integer quantity,
    LocalDate arrivalDate,
    String worker,
    @Schema(nullable = true) Long orchidGroupId,
    @Schema(nullable = true) Long creationMutationId,
    LocalDateTime createdAt,
    @Schema(nullable = true) LocalDateTime canceledAt,
    @Schema(nullable = true) LocalDate correctionDate,
    @Schema(nullable = true) Long cancellationMutationId,
    @Schema(nullable = true) String cancellationReason,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean cancellationAllowed,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean recordOnly) {
  public static DirectReturnArrivalResponse from(DirectReturnArrival a) {
    return new DirectReturnArrivalResponse(
        a.getId(),
        a.getReturnChangeId(),
        a.getQuantity(),
        a.getArrivalDate(),
        a.getWorker(),
        a.getOrchidGroupId(),
        a.getCreationMutationId(),
        a.getCreatedAt(),
        a.getCanceledAt(),
        a.getCorrectionDate(),
        a.getCancellationMutationId(),
        a.getCancellationReason(),
        a.isCancellationAllowed(),
        a.getCreationMutationId() == null);
  }
}
