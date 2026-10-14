package com.greenhouse.backend.work.operation.web.dto;

import com.greenhouse.backend.work.api.operation.WorkTypeTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WorkTypeCreateRequest(
    @NotBlank @Size(max = 50) String name, @NotNull WorkTypeTemplate template) {}
