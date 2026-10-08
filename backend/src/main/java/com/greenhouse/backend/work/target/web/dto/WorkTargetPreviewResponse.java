package com.greenhouse.backend.work.target.web.dto;

import com.greenhouse.backend.work.api.target.WorkOperationTargetView;
import java.util.List;

public record WorkTargetPreviewResponse(
    int orchidGroupCount, int totalQuantity, List<WorkOperationTargetView> targets) {}
