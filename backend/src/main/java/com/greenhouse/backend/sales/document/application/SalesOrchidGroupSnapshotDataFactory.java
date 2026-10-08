package com.greenhouse.backend.sales.document.application;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.sales.api.document.SalesOrchidGroupSnapshotData;
import com.greenhouse.backend.sales.document.domain.SalesOrchidGroupSnapshot;

final class SalesOrchidGroupSnapshotDataFactory {

  private SalesOrchidGroupSnapshotDataFactory() {}

  public static SalesOrchidGroupSnapshotData from(SalesOrchidGroupSnapshot snapshot) {
    if (snapshot == null) {
      return null;
    }
    return new SalesOrchidGroupSnapshotData(
        snapshot.getSnapshotType(),
        snapshot.getCaptureSource(),
        TimeConfig.toFarmTime(snapshot.getCapturedAt()),
        snapshot.getOrchidGroupId(),
        snapshot.getVarietyId(),
        snapshot.getVarietyName(),
        snapshot.getGenus(),
        snapshot.getAgeYear(),
        snapshot.getPotSizeCode(),
        snapshot.getPotSize(),
        snapshot.getQuantity(),
        snapshot.getReservedQuantity(),
        snapshot.getStatus(),
        snapshot.getAllocatedQuantity(),
        snapshot.getHouseId(),
        snapshot.getHouseNumber(),
        snapshot.getPhysicalBedId(),
        snapshot.getPhysicalBedNumber(),
        snapshot.getBedZoneId(),
        snapshot.getBedZoneName(),
        snapshot.getStartPosition(),
        snapshot.getEndPosition());
  }
}
