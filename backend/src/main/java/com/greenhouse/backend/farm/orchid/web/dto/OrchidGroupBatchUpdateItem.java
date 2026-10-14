package com.greenhouse.backend.farm.orchid.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record OrchidGroupBatchUpdateItem(
    @NotNull Long orchidGroupId, @NotNull @Valid OrchidGroupUpdateRequest update) {}
