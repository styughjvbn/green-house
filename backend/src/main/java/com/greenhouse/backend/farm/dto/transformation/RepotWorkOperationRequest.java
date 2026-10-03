package com.greenhouse.backend.farm.dto.transformation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RepotWorkOperationRequest(
    @NotBlank @Size(max = 100) String idempotencyKey,
    @NotBlank @Size(max = 150) String title,
    @NotNull LocalDate workDate,
    @Size(max = 100) String worker,
    @Size(max = 1000) String memo,
    @NotNull Long sourceOrchidGroupId,
    @NotNull @Min(1) Integer inputQuantity,
    @NotEmpty @Size(max = 100) List<@Valid RepotResultOrchidGroupRequest> results,
    @Size(max = 20) Set<@NotNull Long> inheritCollectionIds) {
  public RepotWorkOperationRequest {
    inheritCollectionIds = inheritCollectionIds == null ? Set.of() : inheritCollectionIds;
    if (inheritCollectionIds.stream().noneMatch(Objects::isNull)) {
      inheritCollectionIds = Collections.unmodifiableSortedSet(new TreeSet<>(inheritCollectionIds));
    }
    idempotencyKey = idempotencyKey == null ? null : idempotencyKey.trim();
  }
}
