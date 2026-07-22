package com.greenhouse.backend.work.dto.target;

import java.util.List;

public record WorkTargetPreviewResponse(
		int orchidGroupCount,
		int totalQuantity,
		List<WorkOperationTargetResponse> targets) {
}
