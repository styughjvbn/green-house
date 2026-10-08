package com.greenhouse.backend.work.dto.target;

import com.greenhouse.backend.work.api.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.application.target.WorkTargetSelectionInput;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record WorkTargetPreviewRequest(
    @NotNull WorkSourceScopeType sourceScopeType,
    Long sourceScopeId,
    String sourceDerivedGroupKey,
    List<Long> sourceOrchidGroupIds)
    implements WorkTargetSelectionInput {}
