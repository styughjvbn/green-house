package com.greenhouse.backend.farm.dto.orchid;

import com.greenhouse.backend.farm.domain.orchid.PotSizeCode;

public record DerivedOrchidGroupResponse(
		String groupKey,
		Long varietyId,
		String varietyName,
		String genus,
		Integer ageYear,
		PotSizeCode potSizeCode,
		String potSize,
		int orchidGroupCount,
		int totalQuantity,
		int locationCount) {
}
