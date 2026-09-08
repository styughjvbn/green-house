package com.greenhouse.backend.farm.dto.status;

import java.math.BigDecimal;

public record FarmStatusMapOrchidGroupResponse(
		Long orchidGroupId,
		Long houseId,
		Long physicalBedId,
		Long bedZoneId,
		BigDecimal startPosition,
		BigDecimal endPosition,
		Long varietyId,
		String varietyColor,
		String varietyName,
		Integer quantity,
		String status,
		Integer ageYear,
		String potSize,
		Integer sortOrder) {

}
