package com.greenhouse.backend.farm.transformation.repository;

import com.greenhouse.backend.farm.transformation.domain.OrchidGroupLineageRelationType;

public record GraphLineageTypeRow(
    Long mutationId, Long resultOrchidGroupId, OrchidGroupLineageRelationType relationType) {}
