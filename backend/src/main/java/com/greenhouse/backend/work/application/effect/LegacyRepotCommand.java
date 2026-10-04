package com.greenhouse.backend.work.application.effect;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Keeps the legacy request shape, including metadata used in existing receipt fingerprints. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LegacyRepotCommand(
    String idempotencyKey,
    String title,
    LocalDate workDate,
    String worker,
    String memo,
    Long sourceOrchidGroupId,
    Integer inputQuantity,
    List<Result> results,
    Set<Long> inheritCollectionIds)
    implements WorkEffectPayload {
  public LegacyRepotCommand {
    inheritCollectionIds = inheritCollectionIds == null ? Set.of() : inheritCollectionIds;
    if (inheritCollectionIds.stream().noneMatch(Objects::isNull)) {
      inheritCollectionIds = Collections.unmodifiableSortedSet(new TreeSet<>(inheritCollectionIds));
    }
    idempotencyKey = idempotencyKey == null ? null : idempotencyKey.trim();
  }

  public record Result(
      Long bedZoneId,
      Integer quantity,
      String potSize,
      Integer ageYear,
      String placementType,
      Integer trayCount,
      Boolean splitPlacementAllowed,
      BigDecimal startPosition,
      BigDecimal endPosition,
      String memo) {}
}
