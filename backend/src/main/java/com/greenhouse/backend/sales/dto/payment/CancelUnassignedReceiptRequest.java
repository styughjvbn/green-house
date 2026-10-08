package com.greenhouse.backend.sales.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CancelUnassignedReceiptRequest(
    @NotNull LocalDate correctionDate,
    @NotBlank @Size(max = 1000) String reason,
    @NotBlank @Size(max = 100) String idempotencyKey) {}
