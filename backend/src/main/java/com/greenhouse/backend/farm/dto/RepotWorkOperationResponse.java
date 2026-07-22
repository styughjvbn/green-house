package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.work.dto.operation.WorkOperationResponse;
import java.util.List;

public record RepotWorkOperationResponse(
		WorkOperationResponse operation,
		OrchidGroupResponse sourceOrchidGroup,
		List<OrchidGroupResponse> resultOrchidGroups,
		Integer inputQuantity,
		Integer lossQuantity,
		String lossReason) {
}
