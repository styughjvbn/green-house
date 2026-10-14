package com.greenhouse.backend.work.api.effect;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "StructureChangeLineageEffectView")
public record StructureChangeLineageEffectView(
    Long id,
    Long workOperationId,
    String handlerCode,
    LocalDateTime appliedAt,
    Integer lossQuantity,
    Integer increaseQuantity,
    List<StructureChangeLineageGroupView> sources,
    List<StructureChangeLineageGroupView> results,
    @JsonIgnore @Schema(hidden = true) String structureTypeCode) {}
