package com.greenhouse.backend.farm.dto.status;

import com.greenhouse.backend.farm.dto.orchid.OrchidGroupResponse;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import java.math.BigDecimal;

public record FarmStatusMapOrchidGroupResponse(
		Long orchidGroupId,
		Long houseId,
		Long physicalBedId,
		Long bedZoneId,
		BigDecimal startPosition,
		BigDecimal endPosition,
		Long varietyId,
		String varietyName,
		Integer quantity,
		String status,
		Integer ageYear,
		String potSize,
		Integer sortOrder) {

	public static FarmStatusMapOrchidGroupResponse from(OrchidGroup orchidGroup) {
		var detail = OrchidGroupResponse.from(orchidGroup);
		return new FarmStatusMapOrchidGroupResponse(
				orchidGroup.getId(),
				detail.houseId(),
				orchidGroup.getBedZone().getPhysicalBed().getId(),
				detail.bedZoneId(),
				orchidGroup.getStartPosition(),
				orchidGroup.getEndPosition(),
				detail.varietyId(),
				detail.varietyName(),
				detail.quantity(),
				detail.status(),
				detail.ageYear(),
				detail.potSize(),
				detail.sortOrder());
	}
}
