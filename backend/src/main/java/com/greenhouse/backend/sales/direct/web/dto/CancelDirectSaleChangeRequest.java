package com.greenhouse.backend.sales.direct.web.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CancelDirectSaleChangeRequest(
    @NotNull LocalDate changeDate, @NotBlank @Size(max = 500) String reason) {}
