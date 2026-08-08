package com.greenhouse.backend.farm.application.orchid;

import java.math.BigDecimal;

public record OrchidGroupAuditSnapshot(
		Long varietyId,
		Integer ageYear,
		String potSize,
		Integer quantity,
		Long houseId,
		Long physicalBedId,
		Long zoneId,
		BigDecimal startPosition,
		BigDecimal endPosition,
		String status
) {
}
