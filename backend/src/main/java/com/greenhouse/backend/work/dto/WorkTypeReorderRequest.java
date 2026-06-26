package com.greenhouse.backend.work.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record WorkTypeReorderRequest(
	@NotEmpty List<Long> orderedIds
) {
}
