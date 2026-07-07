package com.greenhouse.backend.farm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import com.greenhouse.backend.farm.domain.PlacementCapacityMode;
import java.time.LocalDate;
import java.util.List;

public record OrchidGroupMoveRequest(
		@NotNull Long toBedZoneId,
		PlacementCapacityMode placementMode,
		List<@Valid OrchidGroupMovePlacementRequest> placements,
		LocalDate reorganizeDueDate,
		@Size(max = 100) String worker,
		@Size(max = 1000) String memo) {
}
