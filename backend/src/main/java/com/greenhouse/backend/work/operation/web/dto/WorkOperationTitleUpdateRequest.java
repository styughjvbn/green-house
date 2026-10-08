package com.greenhouse.backend.work.operation.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkOperationTitleUpdateRequest(@NotBlank @Size(max = 150) String title) {}
