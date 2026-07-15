package com.greenhouse.backend.work.dto;

import com.greenhouse.backend.farm.dto.OrchidGroupResponse;
import java.util.List;

public record MultiCreateWorkOperationResponse(
		WorkOperationResponse operation,
		List<OrchidGroupResponse> createdOrchidGroups) {
}
