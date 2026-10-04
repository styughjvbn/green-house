package com.greenhouse.backend.work.application.effect;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.greenhouse.backend.work.domain.operation.WorkTypeDefinition;
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
    List<StructureChangeLineageGroupView> results) {

  @JsonIgnore
  public WorkTypeDefinition structureType() {
    return WorkTypeDefinition.forStoredStructureHandler(handlerCode)
        .orElseThrow(
            () -> new IllegalStateException("구조 변경 계보의 저장 handler를 해석할 수 없습니다: " + handlerCode));
  }
}
