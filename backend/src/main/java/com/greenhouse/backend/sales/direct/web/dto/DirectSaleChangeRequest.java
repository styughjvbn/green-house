package com.greenhouse.backend.sales.direct.web.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record DirectSaleChangeRequest(
    @NotNull @Positive Long itemId,
    @Positive Long sourceChangeId,
    @NotNull @Positive Integer quantity,
    @PositiveOrZero Integer unitPrice,
    @NotNull LocalDate changeDate,
    @NotBlank @Size(max = 500) String reason) {}
