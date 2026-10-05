package com.greenhouse.backend.farm.repository.transformation;

import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineageRelationType;

public record GraphLineageTypeRow(
    Long mutationId, Long resultOrchidGroupId, OrchidGroupLineageRelationType relationType) {}
