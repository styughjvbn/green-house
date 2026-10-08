package com.greenhouse.backend.farm.transformation.web.dto;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupResponse;
import com.greenhouse.backend.farm.transformation.domain.OrchidGroupLineage;
import com.greenhouse.backend.farm.transformation.domain.OrchidGroupLineageRelationType;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record OrchidGroupLineageItemResponse(
    Long id,
    OrchidGroupLineageRelationType relationType,
    Long workOperationId,
    Integer sourceQuantity,
    Integer resultQuantity,
    LocalDateTime createdAt,
    OrchidGroupResponse sourceOrchidGroup,
    OrchidGroupResponse resultOrchidGroup) {

  public static OrchidGroupLineageItemResponse from(
      OrchidGroupLineage lineage, LocalDate businessDate) {
    return new OrchidGroupLineageItemResponse(
        lineage.getId(),
        lineage.getRelationType(),
        lineage.getWorkOperationId(),
        lineage.getSourceQuantity(),
        lineage.getResultQuantity(),
        TimeConfig.toFarmTime(lineage.getCreatedAt()),
        OrchidGroupResponse.from(lineage.getSourceOrchidGroup(), businessDate),
        OrchidGroupResponse.from(lineage.getResultOrchidGroup(), businessDate));
  }
}
