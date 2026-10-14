package com.greenhouse.backend.farm.inbound.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record InboundRecordUpdateRequest(
    @NotNull LocalDate inboundDate,
    @Min(1) Integer estimatedQuantity,
    @Size(max = 255) String tempLocation,
    LocalDate pottingDueDate,
    @Size(max = 50) String worker,
    @Size(max = 1000) String memo) {}
