package com.greenhouse.backend.farm.application.orchid;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupState;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;

final class OrchidGroupStateFactory {

  private OrchidGroupStateFactory() {}

  static OrchidGroupState from(OrchidGroup group) {
    var zone = group.getBedZone();
    var bed = zone.getPhysicalBed();
    var house = bed.getHouse();
    return new OrchidGroupState(
        group.getId(),
        group.getVariety() == null ? null : group.getVariety().getId(),
        group.getVarietyName(),
        group.getGenus(),
        group.getAgeYear(),
        group.getPotSizeCode() == null ? null : group.getPotSizeCode().name(),
        group.getPotSize(),
        group.getQuantity(),
        group.getReservedQuantity(),
        group.getAvailableQuantity(),
        group.getStatus(),
        house.getId(),
        house.getNumber(),
        bed.getId(),
        bed.getNumber(),
        zone.getId(),
        zone.getName(),
        group.getStartPosition(),
        group.getEndPosition());
  }
}
