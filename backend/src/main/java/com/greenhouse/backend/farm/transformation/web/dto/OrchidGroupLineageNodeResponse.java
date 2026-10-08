package com.greenhouse.backend.farm.transformation.web.dto;

import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupResponse;

public record OrchidGroupLineageNodeResponse(Integer quantity, OrchidGroupResponse orchidGroup) {}
