package com.greenhouse.backend.farm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrchidGroupMoveRequest(
	@NotNull Long toBedZoneId,
	@Size(max = 100) String worker,
	@Size(max = 1000) String memo
) {
}
