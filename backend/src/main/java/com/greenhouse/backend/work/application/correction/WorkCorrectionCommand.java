package com.greenhouse.backend.work.application.correction;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "WorkOperationCorrectionCreateRequest")
public record WorkCorrectionCommand(
    @NotBlank @Size(max = 100) String idempotencyKey,
    @NotNull LocalDate workDate,
    @Size(max = 100) String worker,
    @Size(max = 1000) String memo,
    @NotBlank @Size(max = 1000) String reason,
    @NotNull @Size(max = 100) List<@Valid OrchidGroupCorrectionInput> orchidGroupAdjustments,
    Boolean cancelResultCreation,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Size(max = 100)
        List<@Valid WorkQuantityCorrectionInput> quantityCorrections) {
  public WorkCorrectionCommand(
      String key,
      LocalDate date,
      String worker,
      String memo,
      String reason,
      List<OrchidGroupCorrectionInput> adjustments,
      Boolean cancel) {
    this(key, date, worker, memo, reason, adjustments, cancel, null);
  }

  public WorkCorrectionCommand {
    idempotencyKey = idempotencyKey == null ? null : idempotencyKey.trim();
    cancelResultCreation = Boolean.TRUE.equals(cancelResultCreation);
  }
}
