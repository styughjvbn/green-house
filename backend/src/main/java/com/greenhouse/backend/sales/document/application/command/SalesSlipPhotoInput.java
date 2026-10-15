package com.greenhouse.backend.sales.document.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "SalesSlipPhotoInput")
public record SalesSlipPhotoInput(
    @NotBlank @Size(max = 255) String fileName,
    @NotBlank String contentType,
    @NotBlank @Size(max = 11184812) String contentBase64) {}
