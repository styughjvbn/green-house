package com.greenhouse.backend.farm.repository.orchid;

import java.time.LocalDateTime;

public record OrchidGroupHistoricalStateRow(
		Long orchidGroupId,
		Integer quantity,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {
}
