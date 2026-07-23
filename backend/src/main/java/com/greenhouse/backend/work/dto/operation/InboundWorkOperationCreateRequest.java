package com.greenhouse.backend.work.dto.operation;

import java.time.LocalDate;
import java.util.Map;

public record InboundWorkOperationCreateRequest(
		Long inboundRecordId,
		LocalDate workDate,
		Long varietyId,
		String varietyName,
		Integer quantity,
		String potSize,
		Map<String, Object> locationSnapshot,
		Long createdOrchidGroupId,
		String worker,
		String memo,
		Map<String, Object> details) {
}
