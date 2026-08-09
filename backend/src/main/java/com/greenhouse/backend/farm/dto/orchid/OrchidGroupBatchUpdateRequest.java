package com.greenhouse.backend.farm.dto.orchid;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record OrchidGroupBatchUpdateRequest(
		@NotEmpty List<@Valid OrchidGroupBatchUpdateItem> orchidGroups) {
}
