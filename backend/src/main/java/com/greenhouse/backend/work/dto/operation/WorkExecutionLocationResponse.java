package com.greenhouse.backend.work.dto.operation;

public record WorkExecutionLocationResponse(
		Integer houseNumber,
		Integer physicalBedNumber,
		String bedZoneName) {
}
