package com.greenhouse.backend.sales.document.application;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupState;
import com.greenhouse.backend.sales.api.document.SalesOrchidSnapshotType;
import com.greenhouse.backend.sales.api.document.SalesSlipDocumentAllocation;
import com.greenhouse.backend.sales.document.domain.SalesSlipItemAllocation;

final class SalesSlipDocumentAllocationFactory {

  private SalesSlipDocumentAllocationFactory() {}

  public static SalesSlipDocumentAllocation from(
      SalesSlipItemAllocation allocation, OrchidGroupState group) {
    return new SalesSlipDocumentAllocation(
        allocation.getId(),
        group.id(),
        group.varietyName(),
        allocation.getAllocatedQuantity(),
        group.availableQuantity(),
        group.houseNumber(),
        group.physicalBedNumber(),
        group.bedZoneName(),
        SalesOrchidGroupSnapshotDataFactory.from(
            allocation.findSnapshot(SalesOrchidSnapshotType.CREATION)),
        SalesOrchidGroupSnapshotDataFactory.from(
            allocation.findSnapshot(SalesOrchidSnapshotType.OUTBOUND)));
  }
}
