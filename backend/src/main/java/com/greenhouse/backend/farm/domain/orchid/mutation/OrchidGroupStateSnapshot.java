package com.greenhouse.backend.farm.domain.orchid.mutation;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record OrchidGroupStateSnapshot(
		Integer quantity,
		Integer reservedQuantity,
		String status,
		Long bedZoneId,
		Integer sortOrder,
		BigDecimal startPosition,
		BigDecimal endPosition,
		Long varietyId,
		String genus,
		String varietyName,
		Integer ageYear,
		String potSizeCode,
		String placementType,
		Integer trayCount,
		Boolean splitPlacementAllowed,
		Long inboundRecordId,
		String memo) {

	public static OrchidGroupStateSnapshot from(OrchidGroup group) {
		if (group == null) {
			throw new IllegalArgumentException("난 묶음 snapshot 대상이 필요합니다.");
		}
		return new OrchidGroupStateSnapshot(
				group.getQuantity(),
				group.getReservedQuantity(),
				group.getStatus(),
				group.getBedZone() == null ? null : group.getBedZone().getId(),
				group.getSortOrder(),
				group.getStartPosition(),
				group.getEndPosition(),
				group.getVariety() == null ? null : group.getVariety().getId(),
				group.getGenus(),
				group.getVarietyName(),
				group.getAgeYear(),
				group.getPotSizeCode() == null ? null : group.getPotSizeCode().name(),
				group.getPlacementType(),
				group.getTrayCount(),
				group.getSplitPlacementAllowed(),
				group.getInboundRecord() == null ? null : group.getInboundRecord().getId(),
				group.getMemo());
	}

	public OrchidGroupStateSnapshot canonical() {
		return new OrchidGroupStateSnapshot(
				quantity,
				reservedQuantity,
				status,
				bedZoneId,
				sortOrder,
				canonicalPosition(startPosition),
				canonicalPosition(endPosition),
				varietyId,
				genus,
				varietyName,
				ageYear,
				potSizeCode,
				placementType,
				trayCount,
				splitPlacementAllowed,
				inboundRecordId,
				memo);
	}

	private BigDecimal canonicalPosition(BigDecimal value) {
		return value == null ? null : value.setScale(2, RoundingMode.UNNECESSARY);
	}
}
