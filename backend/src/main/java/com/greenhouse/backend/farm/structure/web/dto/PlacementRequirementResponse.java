package com.greenhouse.backend.farm.structure.web.dto;

public record PlacementRequirementResponse(
    String placementType,
    String potSize,
    Integer quantity,
    Integer occupancyUnits,
    Boolean splitAllowed) {}
