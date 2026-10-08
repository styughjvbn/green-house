package com.greenhouse.backend.farm.dto.transformation;

import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupResponse;

public record OrchidGroupLineageNodeResponse(Integer quantity, OrchidGroupResponse orchidGroup) {}
