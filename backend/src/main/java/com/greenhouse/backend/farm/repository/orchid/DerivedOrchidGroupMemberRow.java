package com.greenhouse.backend.farm.repository.orchid;

import com.greenhouse.backend.farm.domain.orchid.PotSizeCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DerivedOrchidGroupMemberRow(
    Long id,
    Long bedZoneId,
    Long varietyId,
    String varietyColor,
    String genus,
    String varietyName,
    Integer quantity,
    String potSize,
    PotSizeCode potSizeCode,
    Integer baseAgeYear,
    String status,
    String placementType,
    Integer trayCount,
    Boolean splitPlacementAllowed,
    BigDecimal startPosition,
    BigDecimal endPosition,
    Integer sortOrder,
    String memo,
    Long houseId,
    Integer houseNumber,
    Integer physicalBedNumber,
    String bedZoneName,
    LocalDate inboundDate,
    LocalDateTime createdAt) {}
