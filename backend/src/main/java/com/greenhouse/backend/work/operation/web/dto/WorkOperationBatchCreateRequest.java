package com.greenhouse.backend.work.operation.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record WorkOperationBatchCreateRequest(
    @NotNull @Valid WorkOperationCreateRequest operation) {}
