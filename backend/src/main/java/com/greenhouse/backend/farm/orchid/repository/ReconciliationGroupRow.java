package com.greenhouse.backend.farm.orchid.repository;

import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.orchid.domain.PotSizeCode;
import java.math.BigDecimal;

public record ReconciliationGroupRow(
    Long id,
    Long stateRevision,
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
    PotSizeCode potSizeCode,
    String placementType,
    Integer trayCount,
    Boolean splitPlacementAllowed,
    Long inboundRecordId,
    String memo,
    BigDecimal maximumPosition) {
  public OrchidGroupStateSnapshot snapshot() {
    return new OrchidGroupStateSnapshot(
            quantity,
            reservedQuantity,
            status,
            bedZoneId,
            sortOrder,
            startPosition,
            endPosition,
            varietyId,
            genus,
            varietyName,
            ageYear,
            potSizeCode == null ? null : potSizeCode.name(),
            placementType,
            trayCount,
            splitPlacementAllowed,
            inboundRecordId,
            memo)
        .canonical();
  }
}
