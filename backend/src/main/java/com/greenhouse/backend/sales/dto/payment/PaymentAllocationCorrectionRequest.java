package com.greenhouse.backend.sales.dto.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record PaymentAllocationCorrectionRequest(
    @NotNull LocalDate correctionDate,
    @NotEmpty @Size(max = 100) List<@NotNull @Positive Long> cancellationIds,
    @NotNull @Size(max = 100) List<@NotNull @Valid PaymentAllocationLine> allocations,
    @NotBlank @Size(max = 1000) String reason,
    @NotBlank @Size(max = 100) String idempotencyKey,
    @Size(max = 100) String worker) {}
