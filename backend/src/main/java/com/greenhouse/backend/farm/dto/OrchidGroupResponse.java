package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.domain.PotSizeCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record OrchidGroupResponse(
		Long id,
		Long bedZoneId,
		Long varietyId,
		String genus,
		String varietyName,
		Integer quantity,
		String potSize,
		PotSizeCode potSizeCode,
		Integer ageYear,
		String status,
		String placementType,
		Integer trayCount,
		Boolean splitPlacementAllowed,
		BigDecimal startPosition,
		BigDecimal endPosition,
		Integer sortOrder,
		String memo,
		Long houseId,
		Integer houseNumber,
		Integer physicalBedNumber,
		String bedZoneName) {

	public static OrchidGroupResponse from(OrchidGroup orchidGroup) {
		var bedZone = orchidGroup.getBedZone();
		var physicalBed = bedZone.getPhysicalBed();
		var house = physicalBed.getHouse();
		var variety = orchidGroup.getVariety();
		return new OrchidGroupResponse(
				orchidGroup.getId(),
				bedZone.getId(),
				variety != null ? variety.getId() : null,
				variety != null ? variety.getGenus() : orchidGroup.getGenus(),
				variety != null ? variety.getName() : orchidGroup.getVarietyName(),
				orchidGroup.getQuantity(),
				orchidGroup.getPotSize(),
				orchidGroup.getPotSizeCode(),
				calculateAgeYear(orchidGroup),
				orchidGroup.getStatus(),
				orchidGroup.getPlacementType(),
				orchidGroup.getTrayCount(),
				orchidGroup.getSplitPlacementAllowed(),
				orchidGroup.getStartPosition(),
				orchidGroup.getEndPosition(),
				orchidGroup.getSortOrder(),
				orchidGroup.getMemo(),
				house.getId(),
				house.getNumber(),
				physicalBed.getNumber(),
				bedZone.getName());
	}

	private static Integer calculateAgeYear(OrchidGroup orchidGroup) {
		Integer baseAgeYear = orchidGroup.getAgeYear();
		if (baseAgeYear == null) {
			return null;
		}

		LocalDate referenceDate = orchidGroup.getInboundRecord() != null
				? orchidGroup.getInboundRecord().getInboundDate()
				: orchidGroup.getCreatedAt() != null
						? TimeConfig.toFarmTime(orchidGroup.getCreatedAt()).toLocalDate()
						: null;
		if (referenceDate == null) {
			return baseAgeYear;
		}

		long elapsedYears = ChronoUnit.YEARS.between(referenceDate, LocalDate.now(TimeConfig.FARM_TIME_ZONE));
		return baseAgeYear + Math.max(0, Math.toIntExact(elapsedYears));
	}
}
