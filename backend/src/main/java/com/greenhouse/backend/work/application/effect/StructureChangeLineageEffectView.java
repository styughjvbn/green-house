package com.greenhouse.backend.work.application.effect;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "StructureChangeLineageEffectView")
public record StructureChangeLineageEffectView(Long id, Long workOperationId, String handlerCode,
		LocalDateTime appliedAt, Integer lossQuantity, List<StructureChangeLineageGroupView> sources,
		List<StructureChangeLineageGroupView> results) {
}
