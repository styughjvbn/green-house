package com.greenhouse.backend.farm.dto.orchid;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupStateSnapshot;
import java.math.BigDecimal;

public record OrchidGroupMutationStateResponse(
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

  public static OrchidGroupMutationStateResponse from(OrchidGroupStateSnapshot snapshot) {
    if (snapshot == null) {
      return null;
    }
    return new OrchidGroupMutationStateResponse(
        snapshot.quantity(),
        snapshot.reservedQuantity(),
        snapshot.status(),
        snapshot.bedZoneId(),
        snapshot.sortOrder(),
        snapshot.startPosition(),
        snapshot.endPosition(),
        snapshot.varietyId(),
        snapshot.genus(),
        snapshot.varietyName(),
        snapshot.ageYear(),
        snapshot.potSizeCode(),
        snapshot.placementType(),
        snapshot.trayCount(),
        snapshot.splitPlacementAllowed(),
        snapshot.inboundRecordId(),
        snapshot.memo());
  }
}
