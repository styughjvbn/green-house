package com.greenhouse.backend.farm.dto.transformation;

import com.greenhouse.backend.farm.dto.orchid.OrchidGroupResponse;

public record OrchidGroupLineageNodeResponse(Integer quantity, OrchidGroupResponse orchidGroup) {
}
