package com.greenhouse.backend.sales.payment.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record PaymentAllocationRequest(
    @NotNull LocalDate allocationDate,
    @NotEmpty @Size(max = 100) List<@NotNull @Valid PaymentAllocationLine> allocations,
    @NotBlank @Size(max = 100) String idempotencyKey,
    @Size(max = 100) String worker) {}
