package com.greenhouse.backend.work.dto.operation;

import com.greenhouse.backend.work.application.operation.WorkExecutionLocation;
import java.math.BigDecimal;

public record WorkExecutionResultResponse(Long orchidGroupId, Integer quantity, String purpose, Long bedZoneId,
		BigDecimal startPosition, BigDecimal endPosition, String potSize, Integer ageYear, String placementType,
		Integer trayCount, String memo, String varietyName, WorkExecutionLocation location) {
}
