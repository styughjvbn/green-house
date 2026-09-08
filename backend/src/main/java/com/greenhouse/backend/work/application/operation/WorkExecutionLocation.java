package com.greenhouse.backend.work.application.operation;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(name = "WorkExecutionLocationResponse")
public record WorkExecutionLocation(
		Integer houseNumber,
		Integer physicalBedNumber,
		String bedZoneName) {
}
