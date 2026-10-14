package com.greenhouse.backend.sales.api.document;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(name = "SalesOrchidGroupSnapshotResponse")
public record SalesOrchidGroupSnapshotData(
    SalesOrchidSnapshotType snapshotType,
    SalesOrchidSnapshotSource captureSource,
    LocalDateTime capturedAt,
    Long orchidGroupId,
    Long varietyId,
    String varietyName,
    String genus,
    Integer ageYear,
    String potSizeCode,
    String potSize,
    Integer quantity,
    Integer reservedQuantity,
    String status,
    Integer allocatedQuantity,
    Long houseId,
    Integer houseNumber,
    Long physicalBedId,
    Integer physicalBedNumber,
    Long bedZoneId,
    String bedZoneName,
    BigDecimal startPosition,
    BigDecimal endPosition) {}
