package com.greenhouse.backend.farm.dto.transformation;

import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineageRelationType;
import java.time.LocalDateTime;
import java.util.List;

public record OrchidGroupLineageTransformationResponse(Long id, OrchidGroupLineageRelationType relationType,
		Long workOperationId, Integer totalInputQuantity, Integer totalResultQuantity, Integer lossQuantity,
		LocalDateTime createdAt, List<OrchidGroupLineageNodeResponse> sources,
		List<OrchidGroupLineageNodeResponse> results) {
}
