package com.greenhouse.backend.farm.dto;

import java.time.LocalDate;

public record VarietyConnectedOrchidGroupResponse(
		Long orchidGroupId,
		String location,
		Integer quantity,
		String status,
		LocalDate latestWorkDate) {
}
