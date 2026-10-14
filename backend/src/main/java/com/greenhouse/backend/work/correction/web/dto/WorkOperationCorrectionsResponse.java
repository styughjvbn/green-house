package com.greenhouse.backend.work.correction.web.dto;

import com.greenhouse.backend.work.api.correction.WorkQuantityBalance;
import com.greenhouse.backend.work.api.operation.WorkOperationView;
import com.greenhouse.backend.work.operation.web.dto.WorkCorrectionDetailResponse;
import java.util.List;

public record WorkOperationCorrectionsResponse(
    WorkOperationView originalOperation,
    List<WorkCorrectionDetailResponse> corrections,
    List<WorkQuantityBalance> quantityBalances,
    boolean quantityCorrectionEnabled) {}
