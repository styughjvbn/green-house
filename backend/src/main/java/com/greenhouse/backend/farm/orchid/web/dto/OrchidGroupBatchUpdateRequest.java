package com.greenhouse.backend.farm.orchid.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record OrchidGroupBatchUpdateRequest(
    @NotEmpty List<@Valid OrchidGroupBatchUpdateItem> orchidGroups) {}
