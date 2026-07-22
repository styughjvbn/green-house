package com.greenhouse.backend.farm.dto.transformation;

import com.greenhouse.backend.farm.dto.orchid.OrchidGroupResponse;
import com.greenhouse.backend.work.dto.operation.WorkOperationResponse;
import java.util.List;

public record MultiCreateWorkOperationResponse(
		WorkOperationResponse operation,
		List<OrchidGroupResponse> createdOrchidGroups) {
}
