package com.greenhouse.backend.farm.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InboundRecordPottingVoidRequest(
    @NotBlank @Size(max = 100) String idempotencyKey, @NotBlank @Size(max = 1000) String reason) {}
