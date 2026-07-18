package com.greenhouse.backend.farm.application;

import com.greenhouse.backend.farm.dto.InboundRecordResponse;
import java.util.List;

public record InboundPottingResult(
		InboundRecordResponse inboundRecord,
		List<Long> createdOrchidGroupIds,
		int actualQuantity) {
}
