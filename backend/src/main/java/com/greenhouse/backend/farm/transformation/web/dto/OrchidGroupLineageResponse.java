package com.greenhouse.backend.farm.transformation.web.dto;

import java.util.List;

public record OrchidGroupLineageResponse(
    Long orchidGroupId,
    List<OrchidGroupLineageItemResponse> sources,
    List<OrchidGroupLineageItemResponse> results,
    List<OrchidGroupLineageTransformationResponse> transformations) {}
