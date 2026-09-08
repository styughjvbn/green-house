package com.greenhouse.backend.work.dto.target;

import com.greenhouse.backend.work.application.target.WorkOperationTargetView;
import java.util.List;

public record WorkTargetPreviewResponse(int orchidGroupCount, int totalQuantity,
		List<WorkOperationTargetView> targets) {
}
