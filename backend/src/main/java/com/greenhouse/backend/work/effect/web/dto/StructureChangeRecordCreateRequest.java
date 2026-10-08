package com.greenhouse.backend.work.effect.web.dto;

import com.greenhouse.backend.work.api.effect.StructureChangeCommand;
import com.greenhouse.backend.work.operation.web.dto.WorkOperationCreateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record StructureChangeRecordCreateRequest(
    @NotNull @Valid WorkOperationCreateRequest operation,
    @NotNull @Valid StructureChangeCommand execution) {}
