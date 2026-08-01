package com.greenhouse.backend.farm.dto.transformation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MergeWorkOperationRequest(
		@NotEmpty @Size(max = 100) List<@Valid MergeSourceInputRequest> sources,
		@NotNull @Valid RepotResultOrchidGroupRequest result) {
}
