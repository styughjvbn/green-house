package com.greenhouse.backend.farm.mutation.ledger.domain;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.orchid.domain.OrchidGroup;

public final class OrchidGroupStateSnapshotFactory {

  private OrchidGroupStateSnapshotFactory() {}

  public static OrchidGroupStateSnapshot from(OrchidGroup group) {
    if (group == null) {
      throw new IllegalArgumentException("난 묶음 snapshot 대상이 필요합니다.");
    }
    return new OrchidGroupStateSnapshot(
        group.getQuantity(),
        group.getReservedQuantity(),
        group.getStatus(),
        group.getBedZone() == null ? null : group.getBedZone().getId(),
        group.getSortOrder(),
        group.getStartPosition(),
        group.getEndPosition(),
        group.getVariety() == null ? null : group.getVariety().getId(),
        group.getGenus(),
        group.getVarietyName(),
        group.getAgeYear(),
        group.getPotSizeCode() == null ? null : group.getPotSizeCode().name(),
        group.getPlacementType(),
        group.getTrayCount(),
        group.getSplitPlacementAllowed(),
        group.getInboundRecord() == null ? null : group.getInboundRecord().getId(),
        group.getMemo());
  }
}
