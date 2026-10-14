package com.greenhouse.backend.sales.direct.web.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CancelDirectReturnArrivalRequest(
    @NotNull LocalDate correctionDate, @NotBlank @Size(max = 1000) String reason) {}
