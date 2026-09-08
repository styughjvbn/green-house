package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import java.math.BigDecimal;

/** Current values captured together; no managed entities cross the Farm boundary. */
public record OrchidGroupState(Long id, Long varietyId, String varietyName, String genus, Integer ageYear,
		String potSizeCode, String potSize, Integer quantity, Integer reservedQuantity, Integer availableQuantity,
		String status, Long houseId, Integer houseNumber, Long physicalBedId, Integer physicalBedNumber, Long bedZoneId,
		String bedZoneName, BigDecimal startPosition, BigDecimal endPosition) {

	static OrchidGroupState from(OrchidGroup group) {
		var zone = group.getBedZone();
		var bed = zone.getPhysicalBed();
		var house = bed.getHouse();
		return new OrchidGroupState(group.getId(), group.getVariety() == null ? null : group.getVariety().getId(),
				group.getVarietyName(), group.getGenus(), group.getAgeYear(),
				group.getPotSizeCode() == null ? null : group.getPotSizeCode().name(), group.getPotSize(),
				group.getQuantity(), group.getReservedQuantity(), group.getAvailableQuantity(), group.getStatus(),
				house.getId(), house.getNumber(), bed.getId(), bed.getNumber(), zone.getId(), zone.getName(),
				group.getStartPosition(), group.getEndPosition());
	}
}
