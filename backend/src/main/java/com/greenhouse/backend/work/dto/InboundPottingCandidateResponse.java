package com.greenhouse.backend.work.dto;

import com.greenhouse.backend.work.application.InboundPottingPlanTarget;
import java.time.LocalDate;

public record InboundPottingCandidateResponse(
		Long id,
		String varietyName,
		String status,
		Integer estimatedQuantity,
		Integer actualQuantity,
		String tempLocation,
		LocalDate pottingDueDate,
		String potSize) {
	public static InboundPottingCandidateResponse from(InboundPottingPlanTarget target) {
		return new InboundPottingCandidateResponse(
				target.id(), target.varietyName(), target.status(), target.estimatedQuantity(),
				target.actualQuantity(), target.tempLocation(), target.pottingDueDate(), target.potSize());
	}
}
