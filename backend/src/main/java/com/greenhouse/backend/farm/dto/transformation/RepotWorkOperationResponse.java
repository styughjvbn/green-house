package com.greenhouse.backend.farm.dto.transformation;

import com.greenhouse.backend.farm.dto.orchid.OrchidGroupResponse;
import com.greenhouse.backend.work.application.operation.WorkOperationView;
import java.util.List;

public record RepotWorkOperationResponse(
		WorkOperationView operation,
		OrchidGroupResponse sourceOrchidGroup,
		List<OrchidGroupResponse> resultOrchidGroups,
		Integer inputQuantity,
		Integer lossQuantity) {
}
