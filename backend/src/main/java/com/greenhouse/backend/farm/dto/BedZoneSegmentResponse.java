package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.BedZoneSegment;
import com.greenhouse.backend.farm.domain.BedZoneSegmentType;
import java.util.List;

public record BedZoneSegmentResponse(
	Long id,
	String name,
	BedZoneSegmentType segmentType,
	Integer sortOrder,
	String memo,
	List<BedZoneCapacityResponse> capacities
) {
	public static BedZoneSegmentResponse from(BedZoneSegment segment) {
		return new BedZoneSegmentResponse(
			segment.getId(), segment.getName(), segment.getSegmentType(), segment.getSortOrder(), segment.getMemo(),
			segment.getCapacities().stream().map(BedZoneCapacityResponse::from).toList()
		);
	}
}
