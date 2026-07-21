package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.farm.domain.OrchidGroupLineage;
import com.greenhouse.backend.farm.domain.OrchidGroupLineageRelationType;
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

	public static OrchidGroupLineageItemResponse from(OrchidGroupLineage lineage) {
		return new OrchidGroupLineageItemResponse(
				lineage.getId(),
				lineage.getRelationType(),
				lineage.getWorkOperation().getId(),
				lineage.getSourceQuantity(),
				lineage.getResultQuantity(),
				TimeConfig.toFarmTime(lineage.getCreatedAt()),
				OrchidGroupResponse.from(lineage.getSourceOrchidGroup()),
				OrchidGroupResponse.from(lineage.getResultOrchidGroup()));
	}
}
