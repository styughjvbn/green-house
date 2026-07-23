package com.greenhouse.backend.farm.dto.transformation;

import com.greenhouse.backend.farm.dto.orchid.OrchidGroupCreateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record MultiCreateOrchidGroupRowRequest(
		@NotNull @Valid OrchidGroupCreateRequest orchidGroup,
		@Size(max = 20) Set<@NotNull Long> collectionIds) {
}
