package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.OrchidGroupSegmentPlacement;
import com.greenhouse.backend.farm.domain.PlacementCapacityMode;
import java.time.LocalDate;

public record OrchidGroupSegmentPlacementResponse(
	Long id,
	Long segmentId,
	String segmentName,
	Integer quantity,
	Integer trayCount,
	PlacementCapacityMode placementMode,
	LocalDate reorganizeDueDate,
	String memo
) {
	public static OrchidGroupSegmentPlacementResponse from(OrchidGroupSegmentPlacement placement) {
		return new OrchidGroupSegmentPlacementResponse(
			placement.getId(), placement.getSegment().getId(), placement.getSegment().getName(),
			placement.getQuantity(), placement.getTrayCount(), placement.getPlacementMode(),
			placement.getReorganizeDueDate(), placement.getMemo()
		);
	}
}
