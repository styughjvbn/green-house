package com.greenhouse.backend.sales.api.document;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SalesSlipItemAllocationResponse")
public record SalesSlipDocumentAllocation(
    Long id,
    Long orchidGroupId,
    String varietyName,
    Integer allocatedQuantity,
    Integer availableQuantity,
    Integer houseNumber,
    Integer physicalBedNumber,
    String bedZoneName,
    SalesOrchidGroupSnapshotData creationSnapshot,
    SalesOrchidGroupSnapshotData outboundSnapshot) {}
