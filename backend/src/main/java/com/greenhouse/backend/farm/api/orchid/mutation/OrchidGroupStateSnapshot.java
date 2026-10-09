package com.greenhouse.backend.farm.api.orchid.mutation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record OrchidGroupStateSnapshot(
    Integer quantity,
    Integer reservedQuantity,
    String status,
    Long bedZoneId,
    Integer sortOrder,
    BigDecimal startPosition,
    BigDecimal endPosition,
    Long varietyId,
    String genus,
    String varietyName,
    Integer ageYear,
    String potSizeCode,
    String placementType,
    Integer trayCount,
    Boolean splitPlacementAllowed,
    Long inboundRecordId,
    String memo) {

  public OrchidGroupStateSnapshot canonical() {
    return new OrchidGroupStateSnapshot(
        quantity,
        reservedQuantity,
        status,
        bedZoneId,
        sortOrder,
        canonicalPosition(startPosition),
        canonicalPosition(endPosition),
        varietyId,
        genus,
        varietyName,
        ageYear,
        potSizeCode,
        placementType,
        trayCount,
        splitPlacementAllowed,
        inboundRecordId,
        memo);
  }

  private BigDecimal canonicalPosition(BigDecimal value) {
    return value == null ? null : value.setScale(2, RoundingMode.UNNECESSARY);
  }
}
