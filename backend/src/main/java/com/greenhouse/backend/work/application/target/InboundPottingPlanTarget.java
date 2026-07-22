package com.greenhouse.backend.work.application.target;

import java.time.LocalDate;

public record InboundPottingPlanTarget(
		Long id,
		Long varietyId,
		String varietyName,
		String status,
		Integer estimatedQuantity,
		Integer actualQuantity,
		String tempLocation,
		LocalDate pottingDueDate,
		String potSize) {

	public int currentQuantity(int fallback) {
		Integer current = actualQuantity != null ? actualQuantity : estimatedQuantity;
		return current == null ? fallback : current;
	}
}
