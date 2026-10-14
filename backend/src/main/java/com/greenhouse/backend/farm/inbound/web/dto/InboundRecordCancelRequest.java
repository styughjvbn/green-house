package com.greenhouse.backend.farm.inbound.web.dto;

import jakarta.validation.constraints.Size;

public record InboundRecordCancelRequest(
    @Size(max = 100) String idempotencyKey, @Size(max = 1000) String memo) {}
