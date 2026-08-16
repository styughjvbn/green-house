package com.greenhouse.backend.work.dto.effect;

import java.time.LocalDateTime;
import java.util.List;

public record StructureChangeLineageEffectView(
		Long id,
		Long workOperationId,
		String handlerCode,
		LocalDateTime appliedAt,
		Integer lossQuantity,
		List<StructureChangeLineageGroupView> sources,
		List<StructureChangeLineageGroupView> results) {
}
