package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.BedZoneSegment;
import com.greenhouse.backend.farm.domain.BedZoneSegmentType;
import java.math.BigDecimal;
import java.util.List;

public record BedZoneSegmentResponse(
		Long id,
		String name,
		BedZoneSegmentType segmentType,
		Integer sortOrder,
		BigDecimal startPosition,
		BigDecimal endPosition,
		String memo,
		List<BedZoneCapacityResponse> capacities) {
	public static BedZoneSegmentResponse from(BedZoneSegment segment) {
		return new BedZoneSegmentResponse(
				segment.getId(), segment.getName(), segment.getSegmentType(), segment.getSortOrder(),
				segment.getStartPosition(), segment.getEndPosition(), segment.getMemo(),
				segment.getCapacities().stream().map(BedZoneCapacityResponse::from).toList());
	}
}
