package com.greenhouse.backend.work.dto.operation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

public record WorkOperationBatchCancellationRequest(
    @NotEmpty @Size(max = 100) List<@NotNull @Positive Long> workOperationIds,
    @Size(max = 100) Set<@NotNull @Positive Long> creationCancellationOrchidGroupIds,
    @NotBlank @Size(max = 100) String idempotencyKey,
    @NotBlank @Size(max = 1000) String reason) {
  public WorkOperationBatchCancellationRequest {
    creationCancellationOrchidGroupIds =
        creationCancellationOrchidGroupIds == null ? Set.of() : creationCancellationOrchidGroupIds;
  }
}
