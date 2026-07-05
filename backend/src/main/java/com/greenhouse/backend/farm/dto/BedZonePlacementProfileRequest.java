package com.greenhouse.backend.farm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BedZonePlacementProfileRequest(
	@NotEmpty List<@Valid BedZoneSegmentRequest> segments
) {
}
