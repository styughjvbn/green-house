package com.greenhouse.backend.work.application.effect;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(name = "StructureChangeLineageGroupView")
public record StructureChangeLineageGroupView(
		Long orchidGroupId,
		Integer quantity) {
}
