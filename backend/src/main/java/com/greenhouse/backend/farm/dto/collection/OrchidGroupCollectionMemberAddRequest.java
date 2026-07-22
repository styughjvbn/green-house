package com.greenhouse.backend.farm.dto.collection;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record OrchidGroupCollectionMemberAddRequest(
		@NotEmpty Set<Long> orchidGroupIds,
		@Size(max = 100) String createdBy) {
}
