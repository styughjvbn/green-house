package com.greenhouse.backend.work.dto.operation;

import java.math.BigDecimal;

public record WorkExecutionSourceResponse(Long orchidGroupId, Integer inputQuantity, Integer beforeQuantity,
		Integer afterQuantity, Integer remainingQuantity, String beforeStatus, String afterStatus, Long fromBedZoneId,
		BigDecimal releasedStartPosition, BigDecimal releasedEndPosition) {
}
