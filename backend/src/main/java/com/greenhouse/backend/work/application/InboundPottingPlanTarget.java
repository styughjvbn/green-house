package com.greenhouse.backend.work.application;

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
}
