package com.greenhouse.backend.farm.dto.collection;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.domain.collection.OrchidGroupCollectionMember;
import java.time.LocalDateTime;

public record OrchidGroupCollectionMemberResponse(
		Long membershipId,
		Long orchidGroupId,
		String varietyName,
		Integer quantity,
		String status,
		String potSize,
		Integer ageYear,
		Integer houseNumber,
		Integer physicalBedNumber,
		String bedZoneName,
		LocalDateTime joinedAt) {

	public static OrchidGroupCollectionMemberResponse from(
			OrchidGroupCollectionMember member,
			OrchidGroup group) {
		return new OrchidGroupCollectionMemberResponse(
				member.getId(), group.getId(), group.getVarietyName(), group.getQuantity(), group.getStatus(),
				group.getPotSize(), group.getAgeYear(), group.getBedZone().getPhysicalBed().getHouse().getNumber(),
				group.getBedZone().getPhysicalBed().getNumber(), group.getBedZone().getName(),
				TimeConfig.toFarmTime(member.getJoinedAt()));
	}
}
