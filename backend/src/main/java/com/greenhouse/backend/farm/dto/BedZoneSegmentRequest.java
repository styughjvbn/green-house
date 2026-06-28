package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.farm.domain.BedZoneSegmentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BedZoneSegmentRequest(
	Long id,
	@NotBlank @Size(max = 100) String name,
	@NotNull BedZoneSegmentType segmentType,
	@NotNull @Min(1) Integer sortOrder,
	@Size(max = 1000) String memo,
	@NotNull List<@Valid BedZoneCapacityRequest> capacities
) {
}
