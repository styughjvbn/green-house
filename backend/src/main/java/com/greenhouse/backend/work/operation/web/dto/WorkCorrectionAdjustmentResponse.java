package com.greenhouse.backend.work.operation.web.dto;

public record WorkCorrectionAdjustmentResponse(
    Long orchidGroupId,
    Integer beforeQuantity,
    Integer afterQuantity,
    String beforeStatus,
    String afterStatus) {}
