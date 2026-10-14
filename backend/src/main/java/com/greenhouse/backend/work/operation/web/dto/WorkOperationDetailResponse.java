package com.greenhouse.backend.work.operation.web.dto;

import java.util.List;

public record WorkOperationDetailResponse(
    WorkOperationDetailSummaryResponse summary,
    List<WorkOperationDetailFieldResponse> fields,
    List<WorkExecutionDetailResponse> executions,
    List<WorkCorrectionDetailResponse> corrections) {}
