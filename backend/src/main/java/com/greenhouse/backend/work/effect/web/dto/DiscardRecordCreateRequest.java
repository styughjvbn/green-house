package com.greenhouse.backend.work.effect.web.dto;

import com.greenhouse.backend.work.operation.web.dto.WorkOperationCreateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record DiscardRecordCreateRequest(
    @NotNull @Valid WorkOperationCreateRequest operation,
    @NotNull LocalDate completedDate,
    @Size(max = 100) String worker,
    @NotEmpty @Size(max = 100) List<@Valid DiscardRecordResultRequest> results) {}
